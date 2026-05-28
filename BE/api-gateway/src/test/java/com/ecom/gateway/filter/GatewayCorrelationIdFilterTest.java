package com.ecom.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayCorrelationIdFilterTest {
    private final GatewayCorrelationIdFilter filter = new GatewayCorrelationIdFilter();

    @Test
    void preservesIncomingCorrelationId() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health")
                        .header(GatewayCorrelationIdFilter.HEADER_NAME, "corr-123")
        );
        AtomicReference<String> downstreamHeader = new AtomicReference<>();
        GatewayFilterChain chain = filteredExchange -> {
            downstreamHeader.set(filteredExchange.getRequest().getHeaders().getFirst(GatewayCorrelationIdFilter.HEADER_NAME));
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(downstreamHeader.get()).isEqualTo("corr-123");
        assertThat(exchange.getResponse().getHeaders().getFirst(GatewayCorrelationIdFilter.HEADER_NAME))
                .isEqualTo("corr-123");
    }

    @Test
    void createsCorrelationIdWhenMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health")
        );
        AtomicReference<HttpHeaders> downstreamHeaders = new AtomicReference<>();
        GatewayFilterChain chain = filteredExchange -> {
            downstreamHeaders.set(filteredExchange.getRequest().getHeaders());
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        String responseCorrelationId = exchange.getResponse().getHeaders().getFirst(GatewayCorrelationIdFilter.HEADER_NAME);
        assertThat(responseCorrelationId).isNotBlank();
        assertThat(downstreamHeaders.get().getFirst(GatewayCorrelationIdFilter.HEADER_NAME))
                .isEqualTo(responseCorrelationId);
    }

    @Test
    void webFilterAddsCorrelationIdForInternalEndpoints() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health")
                        .header(GatewayCorrelationIdFilter.HEADER_NAME, "corr-actuator")
        );
        WebFilterChain chain = filteredExchange -> Mono.empty();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getHeaders().getFirst(GatewayCorrelationIdFilter.HEADER_NAME))
                .isEqualTo("corr-actuator");
    }
}
