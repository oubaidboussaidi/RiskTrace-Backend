package com.risktrace.gateway_service.Security;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Predicate;

@Component
public class RouteValidator {

        public static final List<String> openApiEndpoints = List.of(
                        "/api/auth/register",
                        "/api/auth/login",
                        "/api/auth/verify-email",
                        "/api/auth/resend-verification",
                        "/api/auth/forgot-password",
                        "/api/auth/reset-password",
                        "/api/logs/collect",
                        "/tracker.js",
                        "/eureka");

        public Predicate<ServerHttpRequest> isSecured = request -> openApiEndpoints
                        .stream()
                        .noneMatch(uri -> request.getURI().getPath().contains(uri));

}
