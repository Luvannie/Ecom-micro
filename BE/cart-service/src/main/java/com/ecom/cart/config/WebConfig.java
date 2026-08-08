package com.ecom.cart.config;

import com.ecom.common.security.GatewayUserContextFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.util.List;

/**
 * Registers the shared {@link GatewayUserContextFilter} for cart-service
 * paths. Uses {@link FilterRegistrationBean} so cart-service stays free
 * of {@code spring-boot-starter-security} — the filter is still executed
 * by the servlet container, just with explicit URL-pattern scoping.
 */
@Configuration
public class WebConfig {

    @Bean
    public FilterRegistrationBean<GatewayUserContextFilter> gatewayUserContextFilter() {
        FilterRegistrationBean<GatewayUserContextFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new GatewayUserContextFilter(List.of("/api/cart/**")));
        reg.addUrlPatterns("/api/cart/*", "/api/cart/*/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        reg.setName("gatewayUserContextFilter");
        return reg;
    }
}
