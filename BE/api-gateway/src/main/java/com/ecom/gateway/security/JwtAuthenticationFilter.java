package com.ecom.gateway.security;

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Verifies a Keycloak-issued RS256 access token, then forwards the user
 * identity to downstream services via three headers:
 * <ul>
 *   <li>{@code X-User-Id}    — Keycloak {@code sub} claim (user UUID)</li>
 *   <li>{@code X-User-Email} — Keycloak {@code email} claim</li>
 *   <li>{@code X-User-Roles} — comma-separated Keycloak realm roles from
 *       {@code realm_access.roles}</li>
 * </ul>
 *
 * <p>Public paths are bypassed; non-public paths without a valid Bearer
 * token are answered with 401.
 */
@Component
public class JwtAuthenticationFilter implements WebFilter, Ordered {

    private final ReactiveJwtDecoder jwtDecoder;
    private final PublicRouteMatcher publicRouteMatcher;

    public JwtAuthenticationFilter(ReactiveJwtDecoder jwtDecoder,
                                   PublicRouteMatcher publicRouteMatcher) {
        this.jwtDecoder = jwtDecoder;
        this.publicRouteMatcher = publicRouteMatcher;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (publicRouteMatcher.isPublic(path) || !path.startsWith("/api/")) {
            return chain.filter(exchange);
        }

        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authorization.substring(7);
        return jwtDecoder.decode(token)
                .flatMap(jwt -> {
                    ServerHttpRequest request = exchange.getRequest().mutate()
                            .header("X-User-Id", extractUserId(jwt))
                            .header("X-User-Email", extractEmail(jwt))
                            .header("X-User-Roles", extractRoles(jwt))
                            .build();
                    return chain.filter(exchange.mutate().request(request).build());
                })
                .onErrorResume(JwtException.class, ex -> {
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                });
    }

    private String extractUserId(Jwt jwt) {
        String sub = jwt.getSubject();
        return sub == null ? "" : sub;
    }

    private String extractEmail(Jwt jwt) {
        Object email = jwt.getClaim("email");
        return email == null ? "" : email.toString();
    }

    @SuppressWarnings("unchecked")
    private String extractRoles(Jwt jwt) {
        Object realmAccess = jwt.getClaim("realm_access");
        if (realmAccess instanceof Map<?, ?> map) {
            Object roles = map.get("roles");
            if (roles instanceof List<?> list) {
                return list.stream()
                        .filter(r -> r != null)
                        .map(Object::toString)
                        .collect(Collectors.joining(","));
            }
        }
        return "";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    /** Small helper kept for callers that still need UUID parsing (tests). */
    static UUID parseUserId(String sub) {
        return UUID.fromString(sub);
    }
}
