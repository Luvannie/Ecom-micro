package com.ecom.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Replaces the legacy SecurityConfig that was built around HS256 + JPA
 * user store. The new auth-service is a thin Keycloak-aware resource
 * server:
 * <ul>
 *   <li>All endpoints require a valid Bearer access token</li>
 *   <li>Tokens are RS256, verified against the Keycloak realm's JWKS
 *       endpoint (configured via {@code keycloak.jwk-set-uri})</li>
 *   <li>Stateless — no session, no cookies</li>
 * </ul>
 *
 * <p>Authentication (login/register/refresh) is now handled entirely by
 * Keycloak (Authorization Code + PKCE on the frontend); the only
 * endpoint this service exposes is {@code /api/auth/me}, which decodes
 * the current JWT to return user identity and realm roles.
 */
@Configuration
@EnableWebSecurity
public class KeycloakResourceServerConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));
        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder(@Value("${keycloak.jwk-set-uri}") String jwkSetUri) {
        return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }
}
