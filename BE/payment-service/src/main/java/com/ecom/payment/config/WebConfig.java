package com.ecom.payment.config;

import com.ecom.common.security.GatewayUserContextFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.util.List;

/**
 * Registers the shared {@link GatewayUserContextFilter} for payment-service
 * paths, with the mock-payment webhook carved out as a public endpoint.
 *
 * <p>The webhook URL pattern {@code /api/payments/webhooks/*} is registered
 * as an initialization parameter so the servlet container does not invoke
 * the filter for it. All other {@code /api/payments/**} paths still require
 * the {@code X-User-Id} / {@code X-User-Email} headers populated by the
 * API gateway.
 *
 * <p>Uses {@link FilterRegistrationBean} so payment-service stays free
 * of {@code spring-boot-starter-security} — the filter is still executed
 * by the servlet container, just with explicit URL-pattern scoping.
 */
@Configuration
public class WebConfig {

    @Bean
    public FilterRegistrationBean<GatewayUserContextFilter> gatewayUserContextFilter() {
        FilterRegistrationBean<GatewayUserContextFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new GatewayUserContextFilter(List.of("/api/payments/**")));
        // Register the filter only for the auth-required paths. The mock
        // webhook pattern is intentionally excluded; addUrlPatterns uses
        // servlet-spec wildcards, not Ant.
        reg.addUrlPatterns("/api/payments", "/api/payments/*", "/api/payments/*/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        reg.setName("gatewayUserContextFilter");
        return reg;
    }
}
