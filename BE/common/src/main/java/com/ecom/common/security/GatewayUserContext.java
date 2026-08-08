package com.ecom.common.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

public record GatewayUserContext(UUID userId, String email) {
    public static GatewayUserContext from(HttpServletRequest request) {
        Object rawId = request.getAttribute(GatewayUserContextFilter.USER_ID_ATTRIBUTE);
        Object rawEmail = request.getAttribute(GatewayUserContextFilter.USER_EMAIL_ATTRIBUTE);
        if (rawId == null) {
            throw new IllegalStateException("GatewayUserContextFilter did not populate userId attribute");
        }
        if (rawEmail == null) {
            throw new IllegalStateException("GatewayUserContextFilter did not populate userEmail attribute");
        }
        return new GatewayUserContext(UUID.fromString(rawId.toString()), rawEmail.toString());
    }
}
