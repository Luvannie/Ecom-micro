package com.ecom.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimitConfig {

    @Bean
    public IpKeyResolver ipKeyResolver() {
        return new IpKeyResolver();
    }
}

class IpKeyResolver implements KeyResolver {
    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        String xff = request.getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int commaIdx = xff.indexOf(',');
            String firstIp = commaIdx > 0 ? xff.substring(0, commaIdx).trim() : xff.trim();
            return Mono.just(firstIp);
        }
        if (request.getRemoteAddress() != null && request.getRemoteAddress().getAddress() != null) {
            return Mono.just(request.getRemoteAddress().getAddress().getHostAddress());
        }
        return Mono.just("unknown");
    }
}
