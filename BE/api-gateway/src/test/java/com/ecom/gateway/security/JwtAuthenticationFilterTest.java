package com.ecom.gateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationFilterTest {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String EMAIL_HEADER = "X-User-Email";
    private static final String ROLES_HEADER = "X-User-Roles";

    private StubJwtDecoder jwtDecoder;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        jwtDecoder = new StubJwtDecoder();
        filter = new JwtAuthenticationFilter(jwtDecoder,
                new PublicRouteMatcher(List.of(
                        "/api/auth/register", "/api/auth/login", "/api/auth/refresh",
                        "/actuator/health", "/v3/api-docs", "/swagger-ui")));
    }

    @Test
    void publicRouteDoesNotRequireToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/auth/login"));

        StepVerifier.create(filter.filter(exchange, passThrough())).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void protectedRouteWithoutTokenReturnsUnauthorized() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/users/me"));

        StepVerifier.create(filter.filter(exchange, passThrough())).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void protectedRouteWithMalformedAuthorizationReturnsUnauthorized() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "NotBearer abc"));

        StepVerifier.create(filter.filter(exchange, passThrough())).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void validJwtForwardsUserHeaders() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = stubJwt(userId, "customer@example.com", List.of("customer"));
        jwtDecoder.stubJwtFor(jwt);

        AtomicReference<String> forwardedUserId = new AtomicReference<>();
        AtomicReference<String> forwardedEmail = new AtomicReference<>();
        AtomicReference<String> forwardedRoles = new AtomicReference<>();

        WebFilterChain chain = exchange -> {
            forwardedUserId.set(exchange.getRequest().getHeaders().getFirst(USER_ID_HEADER));
            forwardedEmail.set(exchange.getRequest().getHeaders().getFirst(EMAIL_HEADER));
            forwardedRoles.set(exchange.getRequest().getHeaders().getFirst(ROLES_HEADER));
            return Mono.empty();
        };

        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.getTokenValue()));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(forwardedUserId.get()).isEqualTo(userId.toString());
        assertThat(forwardedEmail.get()).isEqualTo("customer@example.com");
        assertThat(forwardedRoles.get()).isEqualTo("customer");
    }

    @Test
    void adminJwtForwardsAdminRole() {
        Jwt jwt = stubJwt(UUID.randomUUID(), "admin@example.com", List.of("admin"));
        jwtDecoder.stubJwtFor(jwt);

        AtomicReference<String> forwardedRoles = new AtomicReference<>();
        WebFilterChain chain = exchange -> {
            forwardedRoles.set(exchange.getRequest().getHeaders().getFirst(ROLES_HEADER));
            return Mono.empty();
        };

        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/admin/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.getTokenValue()));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(forwardedRoles.get()).isEqualTo("admin");
    }

    @Test
    void invalidTokenReturnsUnauthorized() {
        jwtDecoder.shouldThrow(new BadJwtException("bad token"));

        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"));

        StepVerifier.create(filter.filter(exchange, passThrough())).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void jwtWithoutRealmAccessYieldsEmptyRoles() {
        Jwt jwt = stubJwt(UUID.randomUUID(), "user@example.com", null);
        jwtDecoder.stubJwtFor(jwt);

        AtomicReference<String> forwardedRoles = new AtomicReference<>();
        WebFilterChain chain = exchange -> {
            forwardedRoles.set(exchange.getRequest().getHeaders().getFirst(ROLES_HEADER));
            return Mono.empty();
        };

        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.getTokenValue()));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(forwardedRoles.get()).isEqualTo("");
    }

    private WebFilterChain passThrough() {
        return exchange -> Mono.empty();
    }

    private Jwt stubJwt(UUID userId, String email, List<String> realmRoles) {
        Map<String, Object> headers = Map.of("alg", "RS256");
        Map<String, Object> claims = new java.util.LinkedHashMap<>();
        claims.put("sub", userId.toString());
        claims.put("email", email);
        if (realmRoles != null) {
            claims.put("realm_access", Map.of("roles", realmRoles));
        }
        return new Jwt(
                "stub-token",
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(900),
                headers,
                claims);
    }

    /** Tiny stub of {@link ReactiveJwtDecoder} that returns a preconfigured {@link Jwt}. */
    private static class StubJwtDecoder implements ReactiveJwtDecoder {
        private Jwt nextJwt;
        private JwtException nextException;

        void stubJwtFor(Jwt jwt) {
            this.nextJwt = jwt;
            this.nextException = null;
        }

        void shouldThrow(JwtException ex) {
            this.nextJwt = null;
            this.nextException = ex;
        }

        @Override
        public Mono<Jwt> decode(String token) {
            if (nextException != null) {
                return Mono.error(nextException);
            }
            if (nextJwt == null) {
                return Mono.error(new BadJwtException("No JWT stubbed for this test"));
            }
            return Mono.just(nextJwt);
        }
    }
}
