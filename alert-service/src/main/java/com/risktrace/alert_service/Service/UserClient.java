package com.risktrace.alert_service.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Service
public class UserClient {

    private static final Logger logger = LoggerFactory.getLogger(UserClient.class);
    private final WebClient webClient;

    public UserClient(@Qualifier("userWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Fetches the email of the OWNER for the specified organization.
     */
    public String getOrganizationOwnerEmail(String organizationId) {
        try {
            // Call GET /api/organizations/{id}/members
            List<Map<String, Object>> members = webClient.get()
                    .uri("/api/organizations/" + organizationId + "/members")
                    .retrieve()
                    .bodyToFlux(Map.class)
                    .map(m -> (Map<String, Object>) m)
                    .collectList()
                    .block();

            if (members != null) {
                logger.info("[UserClient] Fetched {} members for org {}", members.size(), organizationId);
                for (Map<String, Object> member : members) {
                    String role = (String) member.get("role");
                    if ("OWNER".equals(role)) {
                        String email = (String) member.get("email");
                        logger.info("[UserClient] Found OWNER for org {}: {}", organizationId, email);
                        return email;
                    }
                }
                logger.warn("[UserClient] No OWNER found in members list for org {}", organizationId);
            } else {
                logger.warn("[UserClient] User-service returned null members for org {}", organizationId);
            }
        } catch (Exception ex) {
            logger.error("[UserClient] Failed to fetch owner for org {}: {}", organizationId, ex.getMessage());
        }
        return null;
    }
}
