package com.ecom.auth.security;

import com.ecom.auth.domain.UserRole;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-04-26T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void createdTokenParsesBackToPrincipal() {
        JwtTokenService service = new JwtTokenService(properties("secret-secret-secret-secret-secret-1234"), CLOCK);
        UUID userId = UUID.randomUUID();

        String token = service.createAccessToken(userId, "customer@example.com", Set.of(UserRole.CUSTOMER));

        JwtPrincipal principal = service.parse(token);
        assertThat(principal.userId()).isEqualTo(userId);
        assertThat(principal.email()).isEqualTo("customer@example.com");
        assertThat(principal.roles()).containsExactly("CUSTOMER");
    }

    @Test
    void tokenSignedWithDifferentSecretIsRejected() {
        JwtTokenService issuer = new JwtTokenService(properties("secret-secret-secret-secret-secret-1234"), CLOCK);
        JwtTokenService verifier = new JwtTokenService(properties("other-secret-secret-secret-secret-123"), CLOCK);

        String token = issuer.createAccessToken(UUID.randomUUID(), "customer@example.com", Set.of(UserRole.CUSTOMER));

        assertThatThrownBy(() -> verifier.parse(token))
                .isInstanceOf(InvalidJwtException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        JwtTokenService issuer = new JwtTokenService(properties("secret-secret-secret-secret-secret-1234"), CLOCK);
        JwtTokenService verifier = new JwtTokenService(properties("secret-secret-secret-secret-secret-1234"),
                Clock.fixed(Instant.parse("2026-04-26T12:16:00Z"), ZoneOffset.UTC));

        String token = issuer.createAccessToken(UUID.randomUUID(), "customer@example.com", Set.of(UserRole.CUSTOMER));

        assertThatThrownBy(() -> verifier.parse(token))
                .isInstanceOf(InvalidJwtException.class);
    }

    private JwtProperties properties(String secret) {
        JwtProperties properties = new JwtProperties();
        properties.setIssuer("ecom-auth-service");
        properties.setSecret(secret);
        properties.setAccessTokenTtlMinutes(15);
        properties.setRefreshTokenTtlDays(30);
        return properties;
    }
}
