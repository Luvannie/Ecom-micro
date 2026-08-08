package com.ecom.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayUserContextTest {

    @Test
    void from_request_returns_userId_and_email() {
        UUID id = UUID.randomUUID();
        HttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute(GatewayUserContextFilter.USER_ID_ATTRIBUTE, id.toString());
        req.setAttribute(GatewayUserContextFilter.USER_EMAIL_ATTRIBUTE, "a@b.test");

        GatewayUserContext ctx = GatewayUserContext.from(req);

        assertThat(ctx.userId()).isEqualTo(id);
        assertThat(ctx.email()).isEqualTo("a@b.test");
    }

    @Test
    void from_request_throws_when_userId_missing() {
        HttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute(GatewayUserContextFilter.USER_EMAIL_ATTRIBUTE, "a@b.test");

        assertThatThrownBy(() -> GatewayUserContext.from(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("userId");
    }
}
