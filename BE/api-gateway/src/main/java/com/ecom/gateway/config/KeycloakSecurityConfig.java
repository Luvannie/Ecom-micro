package com.ecom.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

/**
 * Keycloak-as-Identity-Provider config for the API gateway.
 *
 * <p>Replaces the old HS256 shared-secret flow. The gateway now verifies
 * RS256 access tokens issued by Keycloak via the realm's JWKS endpoint
 * ({@code /realms/ecom/protocol/openid-connect/certs}).
 *
 * <p>{@link com.ecom.gateway.security.JwtAuthenticationFilter} (registered
 * as a {@code WebFilter} with order {@code HIGHEST_PRECEDENCE + 10}) is
 * responsible for extracting the Bearer token, calling this decoder, and
 * forwarding {@code X-User-Id / X-User-Email / X-User-Roles} headers to
 * downstream services. We intentionally do <em>not</em> register a
 * {@code SecurityWebFilterChain} so the legacy filter chain keeps
 * controlling access until all paths are migrated.
 */
@Configuration
@EnableWebFluxSecurity
public class KeycloakSecurityConfig {

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder(
            @Value("${keycloak.jwk-set-uri}") String jwkSetUri) {
        return NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }
}
