package com.ecom.gateway.filter;

import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class GatewayCorrelationIdFilter implements GlobalFilter, WebFilter, Ordered {
    public static final String HEADER_NAME = "X-Correlation-Id";
    private static final String MDC_KEY = "correlationId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerWebExchange mutatedExchange = withCorrelationId(exchange);
        return continueWithCorrelationId(mutatedExchange, () -> chain.filter(mutatedExchange));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerWebExchange mutatedExchange = withCorrelationId(exchange);
        return continueWithCorrelationId(mutatedExchange, () -> chain.filter(mutatedExchange));
    }

    private ServerWebExchange withCorrelationId(ServerWebExchange exchange) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(HEADER_NAME);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        ServerHttpRequest request = exchange.getRequest()
                .mutate()
                .header(HEADER_NAME, correlationId)
                .build();
        ServerWebExchange mutatedExchange = exchange.mutate().request(request).build();
        mutatedExchange.getResponse().getHeaders().set(HEADER_NAME, correlationId);
        return mutatedExchange;
    }

    private Mono<Void> continueWithCorrelationId(ServerWebExchange exchange, ChainInvocation invocation) {
        MDC.put(MDC_KEY, exchange.getRequest().getHeaders().getFirst(HEADER_NAME));
        return invocation.filter()
                .doFinally(signalType -> MDC.remove(MDC_KEY));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @FunctionalInterface
    private interface ChainInvocation {
        Mono<Void> filter();
    }
}
