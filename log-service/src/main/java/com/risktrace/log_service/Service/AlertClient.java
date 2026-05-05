package com.risktrace.log_service.Service;

import com.risktrace.log_service.Model.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
public class AlertClient {

    private static final Logger logger = LoggerFactory.getLogger(AlertClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private final WebClient webClient;

    public AlertClient(@Qualifier("alertWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public void sendAlert(Log log, String type, String severity, String description) {
        if (log == null || log.getOrganizationId() == null) {
            return;
        }

        Map<String, Object> alertPayload = new HashMap<>();
        alertPayload.put("organizationId", log.getOrganizationId());
        alertPayload.put("siteId", log.getSiteId());
        alertPayload.put("type", type);
        alertPayload.put("severity", severity);
        alertPayload.put("description", description);
        alertPayload.put("sourceIp", log.getIpAddress());
        alertPayload.put("targetPath", log.getUrl());
        alertPayload.put("sessionId", log.getSessionId());
        alertPayload.put("anomalyScore", log.getAnomalyScore());
        alertPayload.put("status", "OPEN");

        try {
            webClient.post()
                    .uri("/api/alerts")
                    .bodyValue(alertPayload)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .timeout(TIMEOUT)
                    .doOnError(ex -> logger.error("[AlertClient] Failed to send alert: {}", ex.getMessage()))
                    .onErrorResume(ex -> Mono.empty())
                    .subscribe(); // Fire and forget
        } catch (Exception ex) {
            logger.error("[AlertClient] Exception sending alert: {}", ex.getMessage());
        }
    }
}
