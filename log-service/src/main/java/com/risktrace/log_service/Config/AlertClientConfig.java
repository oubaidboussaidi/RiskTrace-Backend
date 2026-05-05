package com.risktrace.log_service.Config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AlertClientConfig {

    @Bean(name = "alertWebClientBuilder")
    @LoadBalanced
    public WebClient.Builder alertWebClientBuilder() {
        return WebClient.builder();
    }

    @Bean(name = "alertWebClient")
    public WebClient alertWebClient(WebClient.Builder alertWebClientBuilder) {
        return alertWebClientBuilder
                .baseUrl("http://ALERT-SERVICE")
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
