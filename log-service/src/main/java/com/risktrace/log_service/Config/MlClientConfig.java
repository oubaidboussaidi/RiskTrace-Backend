package com.risktrace.log_service.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * MlClientConfig
 * ──────────────────────────────────────────────────────────────────────────────
 * Declares a WebClient bean pre-configured with the RiskTraceML service base URL.
 *
 * The base URL is read from ml.service.url (application.properties),
 * which is itself sourced from the ML_SERVICE_URL environment variable.
 *
 * Follows the same @Configuration pattern as PrimaryMongoConfig / SiteMongoConfig.
 * ──────────────────────────────────────────────────────────────────────────────
 */
@Configuration
public class MlClientConfig {

    @Value("${ml.service.url:http://localhost:8000}")
    private String mlServiceUrl;

    @Bean(name = "mlWebClient")
    public WebClient mlWebClient() {
        return WebClient.builder()
                .baseUrl(mlServiceUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
