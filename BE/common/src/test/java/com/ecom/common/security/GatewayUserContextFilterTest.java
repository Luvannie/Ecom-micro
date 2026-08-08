package com.ecom.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayUserContextFilterTest {

    private static final String PROTECTED = "/api/**";

    @Test
    void sets_attributes_when_headers_present() throws Exception {
        var filter = new GatewayUserContextFilter(List.of(PROTECTED));
        var req = new MockHttpServletRequest("GET", "/api/cart/items");
        req.addHeader("X-User-Id", "11111111-1111-1111-1111-111111111111");
        req.addHeader("X-User-Email", "u@e.test");
        var res = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        var executedReq = (HttpServletRequest) ((MockFilterChain) chain).getRequest();
        assertThat(executedReq.getAttribute(GatewayUserContextFilter.USER_ID_ATTRIBUTE))
                .isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(executedReq.getAttribute(GatewayUserContextFilter.USER_EMAIL_ATTRIBUTE))
                .isEqualTo("u@e.test");
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void returns_401_when_headers_missing_on_protected_path() throws Exception {
        var filter = new GatewayUserContextFilter(List.of(PROTECTED));
        var req = new MockHttpServletRequest("GET", "/api/cart/items");
        var res = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
    }

    @Test
    void returns_401_when_userId_blank_on_protected_path() throws Exception {
        var filter = new GatewayUserContextFilter(List.of(PROTECTED));
        var req = new MockHttpServletRequest("GET", "/api/cart/items");
        req.addHeader("X-User-Id", "   ");
        req.addHeader("X-User-Email", "u@e.test");
        var res = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
    }

    @Test
    void skips_authentication_for_public_paths() throws Exception {
        var filter = new GatewayUserContextFilter(List.of(PROTECTED));
        var req = new MockHttpServletRequest("GET", "/actuator/health");
        var res = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
        var executedReq = (HttpServletRequest) ((MockFilterChain) chain).getRequest();
        assertThat(executedReq.getAttribute(GatewayUserContextFilter.USER_ID_ATTRIBUTE)).isNull();
    }
}
