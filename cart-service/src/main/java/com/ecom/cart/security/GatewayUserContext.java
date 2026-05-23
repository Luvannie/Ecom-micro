package com.ecom.cart.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

public record GatewayUserContext(UUID userId, String email) {
    public static GatewayUserContext from(HttpServletRequest request) {
        return new GatewayUserContext(
                UUID.fromString((String) request.getAttribute(GatewayUserContextFilter.USER_ID_ATTRIBUTE)),
                (String) request.getAttribute(GatewayUserContextFilter.USER_EMAIL_ATTRIBUTE));
    }
}
