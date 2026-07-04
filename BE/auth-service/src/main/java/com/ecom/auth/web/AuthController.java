package com.ecom.auth.web;

import com.ecom.auth.web.dto.CurrentUserResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Thin proxy over Keycloak — the only endpoint the auth-service still
 * exposes. Decodes the Keycloak-issued access token (already verified
 * by {@link com.ecom.auth.config.KeycloakResourceServerConfig}) and
 * returns the user's identity and realm roles.
 *
 * <p>Login, register, refresh, and logout are now handled by Keycloak
 * via the OAuth2/OIDC Authorization Code + PKCE flow on the frontend.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @GetMapping("/me")
    public CurrentUserResponse me(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        String email = jwt.getClaimAsString("email");
        Set<String> roles = extractRealmRoles(jwt);
        return new CurrentUserResponse(userId, email, roles);
    }

    @SuppressWarnings("unchecked")
    private Set<String> extractRealmRoles(Jwt jwt) {
        Object realmAccess = jwt.getClaim("realm_access");
        if (realmAccess instanceof Map<?, ?> map) {
            Object roles = map.get("roles");
            if (roles instanceof List<?> list) {
                return list.stream()
                        .filter(r -> r != null)
                        .map(Object::toString)
                        .collect(Collectors.toSet());
            }
        }
        return Set.of();
    }
}
