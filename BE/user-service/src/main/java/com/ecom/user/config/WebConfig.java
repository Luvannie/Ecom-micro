package com.ecom.user.config;

import com.ecom.common.security.GatewayUserContextFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.util.List;

/**
 * Registers the shared {@link GatewayUserContextFilter} for user-service
 * paths. Uses {@link FilterRegistrationBean} so the migration is uniform
 * with cart / order / payment services.
 *
 * <p>The filter's Ant pattern {@code /api/users/&#42;&#42;} matches the
 * original local filter's {@code startsWith("/api/users/")} check exactly.
 * The servlet URL patterns scope which requests trigger the filter at
 * all; the Ant check inside the filter is the actual auth gate.
 */
@Configuration
public class WebConfig {

    @Bean
    public FilterRegistrationBean<GatewayUserContextFilter> gatewayUserContextFilter() {
        FilterRegistrationBean<GatewayUserContextFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new GatewayUserContextFilter(List.of("/api/users/**")));
        reg.addUrlPatterns(
                "/api/users/*",
                "/api/users/*/*",
                "/api/users/*/*/*",
                "/api/users/*/*/*/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        reg.setName("gatewayUserContextFilter");
        return reg;
    }
}
