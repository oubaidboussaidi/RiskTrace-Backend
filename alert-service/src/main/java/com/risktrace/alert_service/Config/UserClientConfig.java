package com.risktrace.alert_service.Config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class UserClientConfig {

    @Bean(name = "userWebClientBuilder")
    @LoadBalanced
    public WebClient.Builder userWebClientBuilder() {
        return WebClient.builder();
    }

    @Bean(name = "userWebClient")
    public WebClient userWebClient(WebClient.Builder userWebClientBuilder) {
        return userWebClientBuilder
                .baseUrl("http://USER-SERVICE")
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
