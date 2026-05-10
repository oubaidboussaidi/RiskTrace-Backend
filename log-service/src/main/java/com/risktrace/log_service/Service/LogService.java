package com.risktrace.log_service.Service;

import com.risktrace.log_service.DTO.LogRequestDto;
import com.risktrace.log_service.DTO.LogResponseDto;
import com.risktrace.log_service.DTO.MlBatchRequestDto;
import com.risktrace.log_service.DTO.MlPredictionResultDto;
import com.risktrace.log_service.DTO.SessionFeaturesDto;
import com.risktrace.log_service.Model.Log;
import com.risktrace.log_service.Model.SiteRef;
import com.risktrace.log_service.Repository.log.LogRepository;
import com.risktrace.log_service.Repository.site.SiteRefRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Service
public class LogService {

    private static final Logger logger = LoggerFactory.getLogger(LogService.class);

    @Autowired
    private LogRepository logRepository;

    @Autowired
    private SiteRefRepository siteRefRepository;

    @Autowired
    private SessionAggregator sessionAggregator;

    @Autowired
    private MlClient mlClient;

    @Autowired
    private AlertClient alertClient;

    // For Live Tail
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public List<LogResponseDto> getAllLogs() {
        return logRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public List<LogResponseDto> getLogsBySite(String siteId) {
        return logRepository.findBySiteId(siteId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public List<LogResponseDto> getLogsByOrganization(String organizationId) {
        return logRepository.findByOrganizationId(organizationId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public List<LogResponseDto> collectLogs(List<LogRequestDto> dtos, String clientIp) {
        if (dtos == null || dtos.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empty payload");
        }

        String apiKey = dtos.get(0).getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing apiKey");
        }

        Optional<SiteRef> siteOpt = siteRefRepository.findByApiKey(apiKey);
        if (siteOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid apiKey");
        }

        SiteRef site = siteOpt.get();
        if (!"ACTIVE".equalsIgnoreCase(site.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Site is inactive");
        }

        // ── Step 1: Build and save raw log documents ──────────────────────────
        List<Log> enriched = new ArrayList<>();
        for (LogRequestDto dto : dtos) {
            Log log = new Log();
            log.setSiteId(site.getId());
            log.setOrganizationId(site.getOrganizationId());
            log.setApiKey(null); // Explicitly null — not stored long-term
            log.setSessionId(dto.getSessionId());
            log.setType(dto.getType());
            log.setUrl(dto.getUrl());
            log.setMethod(dto.getMethod());
            log.setStatusCode(dto.getStatusCode());
            log.setUserAgent(dto.getUserAgent());
            log.setDevice(dto.getDevice());
            log.setResponseTime(dto.getResponseTime());
            log.setCreatedAt(dto.getCreatedAt());
            // Allow frontend to explicitly set IP/Location if available, fallback to backend resolved IP
            log.setIpAddress(dto.getIpAddress() != null ? dto.getIpAddress() : clientIp);
            log.setCountry(dto.getCountry());
            log.setCity(dto.getCity());
            enriched.add(log);
        }

        List<Log> saved = logRepository.saveAll(enriched);

        // ── Step 2: ML scoring (Rolling Window) ─────────────────────────────
        // IMPORTANT: scoreWithMl() returns a NEW list with persisted scores.
        // We must use this returned list — the original 'saved' list still has null scores.
        List<Log> scoredLogs = saved; // default to unscoredlogs if ML fails
        try {
            scoredLogs = scoreWithMl(saved);
        } catch (Exception ex) {
            logger.error("[LogService] ML scoring failed: {}", ex.getMessage());
        }

        // ── Step 3: Notify live tail with UPDATED scores ───────────────────
        List<LogResponseDto> updatedDtos = scoredLogs.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());

        for (LogResponseDto res : updatedDtos) {
            notifyEmitters(res);
        }

        return updatedDtos;
    }

    /**
     * Groups the saved logs by sessionId, fetches recent history for each session
     * from MongoDB, computes one feature vector per rolling window,
     * calls the ML service, then writes anomalyScore + isAnomaly back to the NEW logs.
     *
     * @param saved  The batch of logs just saved in the current request.
     * @return       The same list of logs, with updated ML scores.
     */
    private List<Log> scoreWithMl(List<Log> saved) {
        if (saved == null || saved.isEmpty()) return saved;

        // 1. Group the 'saved' logs by sessionId to use as fallback/context
        Map<String, List<Log>> currentBatchBySession = saved.stream()
                .filter(l -> l.getSessionId() != null)
                .collect(Collectors.groupingBy(Log::getSessionId));

        List<String> sessionKeys = new ArrayList<>(currentBatchBySession.keySet());
        List<SessionFeaturesDto> sessionsFeatures = new ArrayList<>();

        // 2. Build feature vectors for each session
        for (String sid : sessionKeys) {
            // Fetch history from DB (rolling window of 100)
            List<Log> sessionWindow = logRepository.findTop100BySessionIdOrderByCreatedAtDesc(sid);
            
            // If DB is empty due to indexing lag, use the current batch we just saved
            if (sessionWindow == null || sessionWindow.isEmpty()) {
                sessionWindow = currentBatchBySession.get(sid);
            }
            
            if (sessionWindow == null || sessionWindow.isEmpty()) continue;

            // Ensure chronological order (oldest to newest) for aggregation
            List<Log> chronologicalWindow = new ArrayList<>(sessionWindow);
            Collections.reverse(chronologicalWindow);

            sessionsFeatures.add(sessionAggregator.aggregate(chronologicalWindow));
        }

        if (sessionsFeatures.isEmpty()) return saved;

        // 3. Call ML service
        for (int i = 0; i < sessionKeys.size(); i++) {
            logger.info("[ML-FLOW] Session: {} | Features: {}", sessionKeys.get(i), sessionsFeatures.get(i));
        }
        MlBatchRequestDto batchRequest = new MlBatchRequestDto(sessionsFeatures);
        List<MlPredictionResultDto> results = mlClient.predictBatch(batchRequest);

        if (results == null || results.isEmpty() || results.size() != sessionsFeatures.size()) {
            logger.warn("[LogService] ML service returned {} results for {} sessions. Skipping updates.", 
                        results == null ? 0 : results.size(), sessionsFeatures.size());
            return saved;
        }

        // 4. Map results back to the logs using the sessionKeys index
        for (int i = 0; i < sessionKeys.size(); i++) {
            String sid = sessionKeys.get(i);
            MlPredictionResultDto res = results.get(i);
            
            List<Log> logsToUpdate = currentBatchBySession.get(sid);
            if (logsToUpdate != null) {
                for (Log log : logsToUpdate) {
                    log.setAnomalyScore(res.anomalyScore());
                    boolean isAnom = "ANOMALY".equals(res.prediction());
                    log.setIsAnomaly(isAnom);
                    
                    if (isAnom) {
                        String severity = res.anomalyScore() > 0.8 ? "CRITICAL" : "HIGH";
                        alertClient.sendAlert(log, "ANOMALY_DETECTED", severity, "Machine Learning model detected anomalous behaviour with score: " + res.anomalyScore());
                    }
                }
            }
        }

        // Persist updated scores for the current batch
        return logRepository.saveAll(saved);
    }

    public LogResponseDto markLogAsSuspicious(String logId) {
        Optional<Log> logOpt = logRepository.findById(logId);
        if (logOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Log not found");
        }
        Log log = logOpt.get();
        log.setIsSuspicious(true);
        log = logRepository.save(log);
        
        alertClient.sendAlert(log, "SUSPICIOUS_ACTIVITY", "HIGH", "Log manually marked as suspicious by an analyst.");
        
        return mapToDto(log);
    }

    // ── Live Tail Streaming ──────────────────────────────────────────────────
    public SseEmitter streamLogs() {
        SseEmitter emitter = new SseEmitter(60000L); // 1-minute timeout
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError((e) -> emitters.remove(emitter));

        return emitter;
    }

    private void notifyEmitters(LogResponseDto logDto) {
        List<SseEmitter> deadEmitters = new ArrayList<>();
        emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().name("newLog").data(logDto));
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        });
        emitters.removeAll(deadEmitters);
    }

    // ── Mapping ────────────────────────────────────────────────────────────────
    private LogResponseDto mapToDto(Log log) {
        LogResponseDto dto = new LogResponseDto();
        dto.setId(log.getId());
        dto.setSiteId(log.getSiteId());
        dto.setOrganizationId(log.getOrganizationId());
        dto.setSessionId(log.getSessionId());
        dto.setType(log.getType());
        dto.setUrl(log.getUrl());
        dto.setMethod(log.getMethod());
        dto.setStatusCode(log.getStatusCode());
        dto.setUserAgent(log.getUserAgent());
        dto.setDevice(log.getDevice());
        dto.setResponseTime(log.getResponseTime());
        dto.setCreatedAt(log.getCreatedAt());
        dto.setIpAddress(log.getIpAddress());
        dto.setCountry(log.getCountry());
        dto.setCity(log.getCity());
        dto.setAnomalyScore(log.getAnomalyScore());
        dto.setIsAnomaly(log.getIsAnomaly());
        dto.setIsSuspicious(log.getIsSuspicious());
        return dto;
    }

    public boolean isMlOnline() {
        return mlClient.checkHealth();
    }
}
