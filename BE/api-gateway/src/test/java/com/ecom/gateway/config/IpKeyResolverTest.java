package com.ecom.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

class IpKeyResolverTest {

    private final IpKeyResolver resolver = new IpKeyResolver();

    @Test
    void usesFirstIpFromXForwardedFor() {
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/auth/login")
                .header("X-Forwarded-For", "1.2.3.4, 10.0.0.1")
                .remoteAddress(new InetSocketAddress("5.6.7.8", 1234))
        );

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo("1.2.3.4");
    }

    @Test
    void fallsBackToRemoteAddrWhenNoHeader() {
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/auth/login")
                .remoteAddress(new InetSocketAddress("5.6.7.8", 1234))
        );

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo("5.6.7.8");
    }

    @Test
    void usesXForwardedForSingleValue() {
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/auth/login")
                .header("X-Forwarded-For", "9.9.9.9")
        );

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo("9.9.9.9");
    }
}
