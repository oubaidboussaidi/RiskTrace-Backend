package com.risktrace.log_service.Controller;

import com.risktrace.log_service.Model.Log;
import com.risktrace.log_service.Model.SiteRef;
import com.risktrace.log_service.Repository.log.LogRepository;
import com.risktrace.log_service.Repository.site.SiteRefRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/logs")
@CrossOrigin(origins = "*") // tracker.js sends from any origin
public class LogController {

    @Autowired
    private LogRepository logRepository;

    @Autowired
    private SiteRefRepository siteRefRepository;

    // ── GET all logs (dashboard use) ──────────────────────────────────────────

    @GetMapping
    public List<Log> getAllLogs() {
        return logRepository.findAll();
    }

    @GetMapping("/site/{siteId}")
    public List<Log> getLogsBySite(@PathVariable String siteId) {
        return logRepository.findBySiteId(siteId);
    }

    // ── POST /collect — batch ingestion from tracker.js ───────────────────────
    /**
     * Accepts a JSON array of log entries from the tracker script.
     * Each entry must have an "apiKey" field.
     * Steps:
     * 1. Read apiKey from first log entry (all share the same key in a batch)
     * 2. Look up the site in sitedb
     * 3. Reject the whole batch if the key is invalid or site is inactive
     * 4. Resolve siteId, enrich with server-side data (IP), clear raw apiKey
     * 5. Save all log entries
     */
    @PostMapping("/collect")
    public ResponseEntity<?> collectLogs(
            @RequestBody List<Log> logs,
            HttpServletRequest request) {

        if (logs == null || logs.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Empty payload"));
        }

        // Step 1 — extract apiKey from first entry
        String apiKey = logs.get(0).getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Missing apiKey"));
        }

        // Step 2 — validate apiKey against sitedb
        Optional<SiteRef> siteOpt = siteRefRepository.findByApiKey(apiKey);
        if (siteOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid apiKey"));
        }

        SiteRef site = siteOpt.get();

        // Step 3 — reject if site is not active
        if (!"ACTIVE".equalsIgnoreCase(site.getStatus())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Site is inactive"));
        }

        // Step 4 — enrich each log entry
        String clientIp = extractClientIp(request);
        List<Log> enriched = new ArrayList<>();
        for (Log log : logs) {
            log.setSiteId(site.getId()); // resolve siteId from apiKey
            log.setApiKey(null); // don't persist the raw API key in logs
            log.setIpAddress(clientIp); // enrich with real IP
            // country + city: placeholder for future GeoIP integration
            enriched.add(log);
        }

        // Step 5 — save batch
        List<Log> saved = logRepository.saveAll(enriched);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // ── Legacy single-log endpoint (backward-compat) ──────────────────────────
    @PostMapping("/collect/single")
    public ResponseEntity<?> collectSingleLog(
            @RequestBody Log log,
            HttpServletRequest request) {
        return collectLogs(List.of(log), request);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
