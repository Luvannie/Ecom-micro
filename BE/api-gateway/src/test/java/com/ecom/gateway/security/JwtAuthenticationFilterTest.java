package com.ecom.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationFilterTest {
    private final GatewayJwtProperties properties = properties();
    private final GatewayJwtTokenService tokenService = new GatewayJwtTokenService(properties, Clock.fixed(Instant.parse("2026-04-26T12:00:00Z"), ZoneOffset.UTC));
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tokenService,
            new PublicRouteMatcher(List.of("/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/actuator/health", "/v3/api-docs", "/swagger-ui")));

    @Test
    void publicRouteDoesNotRequireToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/auth/login"));

        filter.filter(exchange, passThrough()).block();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void protectedRouteWithoutTokenReturnsUnauthorized() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/users/me"));

        filter.filter(exchange, passThrough()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void validTokenForwardsUserHeaders() {
        UUID userId = UUID.randomUUID();
        String token = tokenService.createAccessToken(userId, "customer@example.com", List.of("CUSTOMER"));
        AtomicReference<String> forwardedUserId = new AtomicReference<>();
        WebFilterChain chain = exchange -> {
            forwardedUserId.set(exchange.getRequest().getHeaders().getFirst("X-User-Id"));
            return Mono.empty();
        };
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

        filter.filter(exchange, chain).block();

        assertThat(forwardedUserId.get()).isEqualTo(userId.toString());
    }

    @Test
    void invalidTokenReturnsUnauthorized() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"));

        filter.filter(exchange, passThrough()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private WebFilterChain passThrough() {
        return exchange -> Mono.empty();
    }

    private GatewayJwtProperties properties() {
        GatewayJwtProperties properties = new GatewayJwtProperties();
        properties.setIssuer("ecom-auth-service");
        properties.setSecret("local-development-secret-must-be-32-bytes");
        return properties;
    }
}
