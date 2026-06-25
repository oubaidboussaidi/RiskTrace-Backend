package com.risktrace.gateway_service.Security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private RouteValidator validator;

    public AuthenticationFilter() {
        super(Config.class);
    }

    public static class Config {
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {

            if (validator.isSecured.test(exchange.getRequest())) {

                //  Check Authorization header exists or query params (for SSE EventSource)
                String authHeader = exchange.getRequest()
                        .getHeaders()
                        .getFirst(HttpHeaders.AUTHORIZATION);

                String token = null;

                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    token = authHeader.substring(7);
                } else if (exchange.getRequest().getQueryParams().containsKey("token")) {
                    token = exchange.getRequest().getQueryParams().getFirst("token");
                }

                if (token == null) {
                    return onError(exchange, "Missing or Invalid Authorization Token", HttpStatus.UNAUTHORIZED);
                }

                try {
                    // 3️⃣ Validate token at gateway
                    if (!jwtUtils.isTokenValid(token)) {
                        return onError(exchange, "Invalid Token", HttpStatus.UNAUTHORIZED);
                    }

                    // 4️⃣ Extract user info
                    String email = jwtUtils.extractEmail(token);
                    String userId = jwtUtils.extractUserId(token);

                    // ⭐ IMPORTANT: forward Authorization header to downstream service
                    ServerHttpRequest request = exchange.getRequest().mutate()
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .header("X-User-Email", email)
                            .header("X-User-Id", userId)
                            .build();

                    return chain.filter(exchange.mutate().request(request).build());

                } catch (Exception e) {
                    return onError(exchange, "Token Validation Failed", HttpStatus.UNAUTHORIZED);
                }
            }

            return chain.filter(exchange);
        };
    }

    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus httpStatus) {
        exchange.getResponse().setStatusCode(httpStatus);
        return exchange.getResponse().setComplete();
    }
}
