package com.risktrace.log_service.Controller;

import com.risktrace.log_service.DTO.LogRequestDto;
import com.risktrace.log_service.DTO.LogResponseDto;
import com.risktrace.log_service.Service.LogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/logs")
// @CrossOrigin(origins = "*") // Removed to avoid conflict with Gateway CORS
public class LogController {

    @Autowired
    private LogService logService;

    // ── Management endpoints (dashboard use) ───────────────────────────────────

    @GetMapping
    public ResponseEntity<List<LogResponseDto>> getAllLogs() {
        return ResponseEntity.ok(logService.getAllLogs());
    }

    @GetMapping("/site/{siteId}")
    public ResponseEntity<List<LogResponseDto>> getLogsBySite(@PathVariable String siteId) {
        return ResponseEntity.ok(logService.getLogsBySite(siteId));
    }

    @GetMapping("/org/{organizationId}")
    public ResponseEntity<List<LogResponseDto>> getLogsByOrganization(@PathVariable String organizationId) {
        return ResponseEntity.ok(logService.getLogsByOrganization(organizationId));
    }

    @PutMapping("/{logId}/mark-suspicious")
    public ResponseEntity<LogResponseDto> markLogAsSuspicious(@PathVariable String logId) {
        return ResponseEntity.ok(logService.markLogAsSuspicious(logId));
    }

    // ── Live Tail Streaming ──────────────────────────────────────────────────

    @GetMapping("/stream")
    public ResponseEntity<SseEmitter> streamLogs() {
        SseEmitter emitter = logService.streamLogs();
        return new ResponseEntity<>(emitter, HttpStatus.OK);
    }

    // ── POST /collect — batch ingestion from tracker.js ───────────────────────

    @PostMapping("/collect")
    public ResponseEntity<?> collectLogs(
            @RequestBody List<LogRequestDto> logs,
            HttpServletRequest request) {
        try {
            String clientIp = extractClientIp(request);
            List<LogResponseDto> saved = logService.collectLogs(logs, clientIp);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (org.springframework.web.server.ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .body(Map.of("error", ex.getReason()));
        }
    }

    // ── Legacy single-log endpoint (backward-compat) ──────────────────────────
    @PostMapping("/collect/single")
    public ResponseEntity<?> collectSingleLog(
            @RequestBody LogRequestDto log,
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
