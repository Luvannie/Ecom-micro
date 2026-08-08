package com.ecom.common.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

/**
 * Request-scoped view of the authenticated user, populated by
 * {@link GatewayUserContextFilter} after the API gateway has verified the
 * Keycloak access token and forwarded {@code X-User-Id} / {@code X-User-Email}
 * headers.
 *
 * <p>Both attributes are required: a missing userId or userEmail indicates
 * the filter did not run for this request, which is a configuration bug —
 * {@link #from(HttpServletRequest)} therefore fails fast with
 * {@link IllegalStateException} rather than producing a partially-populated
 * context.
 */
public record GatewayUserContext(UUID userId, String email) {
    /**
     * Build a {@code GatewayUserContext} from the current servlet request.
     *
     * @param request the current {@link HttpServletRequest}; must have been
     *                processed by {@link GatewayUserContextFilter} so both
     *                attribute constants are populated
     * @return the resolved context (never {@code null})
     * @throws IllegalStateException if either attribute is missing — the
     *         message names the missing attribute so the misconfiguration is
     *         easy to diagnose
     */
    public static GatewayUserContext from(HttpServletRequest request) {
        Object rawId = request.getAttribute(GatewayUserContextFilter.USER_ID_ATTRIBUTE);
        if (rawId == null) {
            throw new IllegalStateException("GatewayUserContextFilter did not populate userId attribute");
        }
        Object rawEmail = request.getAttribute(GatewayUserContextFilter.USER_EMAIL_ATTRIBUTE);
        if (rawEmail == null) {
            throw new IllegalStateException("GatewayUserContextFilter did not populate userEmail attribute");
        }
        return new GatewayUserContext(UUID.fromString(rawId.toString()), rawEmail.toString());
    }
}
