package com.ecom.payment.config;

import com.ecom.common.security.GatewayUserContextFilter;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the {@code /api/payments/webhooks/&#42;} carve-out.
 *
 * <p>The mock-payment webhook at {@code POST /api/payments/webhooks/mock}
 * is a public endpoint — it must not be routed through the
 * {@link GatewayUserContextFilter}. The auth-required 3-segment URL
 * {@code /api/payments/&#123;paymentId&#125;/refund} must still be routed
 * through the filter.
 *
 * <p>Both URLs have 3 path segments, so a naive {@code /api/payments/&#42;/&#42;}
 * pattern would 401 the webhook. The production registration uses the
 * literal-suffix pattern {@code /api/payments/&#42;/refund} instead, which
 * only matches URLs whose final segment is exactly {@code refund}.
 *
 * <p>This test pins down the URL pattern set so any future refactor of
 * {@link WebConfig} that re-introduces the {@code &#42;/&#42;} pattern
 * (and breaks the webhook) fails fast at unit-test time.
 */
class WebConfigTest {

    @Test
    void gatewayUserContextFilter_bean_registers_filter_for_auth_paths_only() {
        FilterRegistrationBean<?> reg = new WebConfig().gatewayUserContextFilter();
        Collection<String> patterns = reg.getUrlPatterns();

        // Auth-required paths must be present.
        assertThat(patterns).contains(
                "/api/payments",
                "/api/payments/*",
                "/api/payments/*/refund");

        // The generic 3-segment pattern must NOT be present — it would
        // match /api/payments/webhooks/mock and 401 the public webhook.
        assertThat(patterns).doesNotContain("/api/payments/*/*");

        // The webhook path /api/payments/webhooks/mock is intentionally
        // absent — servlet container will not invoke the filter for it.
        assertThat(patterns).noneMatch(p -> p.contains("webhook"));
    }

    @Test
    void gatewayUserContextFilter_bean_wraps_the_shared_filter() {
        FilterRegistrationBean<?> reg = new WebConfig().gatewayUserContextFilter();
        Filter actual = reg.getFilter();

        assertThat(actual).isInstanceOf(GatewayUserContextFilter.class);
    }
}
