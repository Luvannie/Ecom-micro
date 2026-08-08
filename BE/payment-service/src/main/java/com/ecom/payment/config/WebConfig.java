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
 * <p>Servlet-spec URL patterns are used (not Ant) so the webhook
 * {@code /api/payments/webhooks/mock} is NOT covered by
 * {@code /api/payments/&#42;/refund}, which matches only URLs whose final
 * segment is literally {@code refund}. The auth-required paths the filter
 * sees are:
 * <ul>
 *   <li>{@code /api/payments} (1 segment)</li>
 *   <li>{@code /api/payments/&#123;paymentId&#125;} (2 segments)</li>
 *   <li>{@code /api/payments/&#123;paymentId&#125;/refund} (3 segments, literal {@code refund})</li>
 * </ul>
 * and the filter's Ant pattern {@code /api/payments/&#42;&#42;} is the
 * inner auth-gate.
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
        reg.addUrlPatterns(
                "/api/payments",
                "/api/payments/*",
                "/api/payments/*/refund");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        reg.setName("gatewayUserContextFilter");
        return reg;
    }
}
