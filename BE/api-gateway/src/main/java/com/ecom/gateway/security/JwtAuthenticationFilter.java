package com.ecom.gateway.security;

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthenticationFilter implements WebFilter, Ordered {
    private final GatewayJwtTokenService tokenService;
    private final PublicRouteMatcher publicRouteMatcher;

    public JwtAuthenticationFilter(GatewayJwtTokenService tokenService, PublicRouteMatcher publicRouteMatcher) {
        this.tokenService = tokenService;
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
        try {
            GatewayJwtPrincipal principal = tokenService.parse(authorization.substring(7));
            ServerHttpRequest request = exchange.getRequest().mutate()
                    .header("X-User-Id", principal.userId().toString())
                    .header("X-User-Email", principal.email())
                    .header("X-User-Roles", String.join(",", principal.roles()))
                    .build();
            return chain.filter(exchange.mutate().request(request).build());
        } catch (IllegalArgumentException ex) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
