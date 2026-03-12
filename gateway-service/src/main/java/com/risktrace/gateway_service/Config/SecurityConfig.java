package com.risktrace.gateway_service.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {
//    @Bean
//    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
//        return http
//                .csrf(csrf -> csrf.disable())  // Disable CSRF for stateless API
//                .authorizeExchange(exchanges -> exchanges
//                        .pathMatchers(
//                                "/api/auth/register",
//                                "/api/auth/login",
//                                "/api/auth/verify-email",
//                                "/api/auth/resend-verification",
//                                "/api/auth/forgot-password",
//                                "/api/auth/reset-password",
//                                "/api/logs/collect",
//                                "/api/logs/collect/**",
//                                "/tracker.js"
//                        ).permitAll()  // Public endpoints
//                        .anyExchange().authenticated()  // All others require authentication
//                )
//                .build();
//    }
@Bean
public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
    return http
            .csrf(csrf -> csrf.disable())
            .authorizeExchange(exchanges -> exchanges
                    .anyExchange().permitAll()   //  let AuthenticationFilter handle JWT
            )
            .build();
}
}
