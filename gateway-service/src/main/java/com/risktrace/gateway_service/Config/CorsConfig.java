package com.risktrace.gateway_service.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public CorsWebFilter corsWebFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        // ── Log collect endpoint: open to any origin (tracker.js from external sites)
        // ──
        CorsConfiguration logConfig = new CorsConfiguration();
        logConfig.setAllowedOriginPatterns(List.of("*"));
        logConfig.setAllowedMethods(Arrays.asList("POST", "OPTIONS"));
        logConfig.setAllowedHeaders(List.of("*"));
        logConfig.setAllowCredentials(false); // no cookies from external sites
        logConfig.setMaxAge(3600L);
        source.registerCorsConfiguration("/api/logs/collect", logConfig);
        source.registerCorsConfiguration("/api/logs/collect/**", logConfig);

        // ── All other endpoints: Angular frontend only ──
        CorsConfiguration appConfig = new CorsConfiguration();
        appConfig.setAllowedOrigins(List.of("http://localhost:4200"));
        appConfig.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD"));
        appConfig.setAllowedHeaders(List.of("*"));
        appConfig.setAllowCredentials(true);
        appConfig.setMaxAge(3600L);
        source.registerCorsConfiguration("/**", appConfig);

        return new CorsWebFilter(source);
    }
}
