package com.risktrace.log_service.Service;

import com.risktrace.log_service.DTO.LogRequestDto;
import com.risktrace.log_service.DTO.LogResponseDto;
import com.risktrace.log_service.Model.Log;
import com.risktrace.log_service.Model.SiteRef;
import com.risktrace.log_service.Repository.log.LogRepository;
import com.risktrace.log_service.Repository.site.SiteRefRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Service
public class LogService {

    @Autowired
    private LogRepository logRepository;

    @Autowired
    private SiteRefRepository siteRefRepository;

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

        List<Log> enriched = new ArrayList<>();
        for (LogRequestDto dto : dtos) {
            Log log = new Log();
            log.setSiteId(site.getId());
            log.setOrganizationId(site.getOrganizationId());
            log.setApiKey(null); // Explicitly null
            log.setSessionId(dto.getSessionId());
            log.setType(dto.getType());
            log.setUrl(dto.getUrl());
            log.setMethod(dto.getMethod());
            log.setStatusCode(dto.getStatusCode());
            log.setUserAgent(dto.getUserAgent());
            log.setDevice(dto.getDevice());
            log.setResponseTime(dto.getResponseTime());
            log.setCreatedAt(dto.getCreatedAt());
            log.setIpAddress(clientIp);
            enriched.add(log);
        }

        List<Log> saved = logRepository.saveAll(enriched);
        List<LogResponseDto> savedDtos = saved.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());

        // Notify Live Tail subscribers
        for (LogResponseDto res : savedDtos) {
            notifyEmitters(res);
        }

        return savedDtos;
    }

    public LogResponseDto markLogAsSuspicious(String logId) {
        Optional<Log> logOpt = logRepository.findById(logId);
        if (logOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Log not found");
        }
        Log log = logOpt.get();
        // Toggle or set to true. Assuming we just set it to true.
        log.setIsSuspicious(true);
        log = logRepository.save(log);
        return mapToDto(log);
    }

    // ── Live Tail Streaming ──────────────────────────────────────────────────
    public SseEmitter streamLogs() {
        SseEmitter emitter = new SseEmitter(60000L); // 1-minute timeout (adjust as needed)
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
}
