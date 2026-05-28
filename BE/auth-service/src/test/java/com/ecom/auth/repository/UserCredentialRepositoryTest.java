package com.ecom.auth.repository;

import com.ecom.auth.domain.RefreshToken;
import com.ecom.auth.domain.UserCredential;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class UserCredentialRepositoryTest {
    @Autowired
    private UserCredentialRepository users;

    @Autowired
    private RefreshTokenRepository refreshTokens;

    @Test
    void storesEmailLowercaseAndPersistsDefaultRole() {
        users.save(UserCredential.create("CUSTOMER@EXAMPLE.COM", "hash"));

        assertThat(users.findByEmail("customer@example.com"))
                .hasValueSatisfying(user -> {
                    assertThat(user.getEmail()).isEqualTo("customer@example.com");
                    assertThat(user.roleNames()).containsExactly("CUSTOMER");
                });
    }

    @Test
    void refreshTokenCanBeRevokedAndLoadedAgain() {
        UserCredential user = users.save(UserCredential.create("refresh-repo@example.com", "hash"));
        RefreshToken token = refreshTokens.save(new RefreshToken(
                user.getId(),
                "refresh-token-hash",
                Instant.parse("2026-05-26T00:00:00Z"),
                Instant.parse("2026-04-26T00:00:00Z")));

        token.revoke(Instant.parse("2026-04-27T00:00:00Z"));
        refreshTokens.save(token);

        assertThat(refreshTokens.findByTokenHash("refresh-token-hash"))
                .hasValueSatisfying(loaded -> assertThat(loaded.isRevoked()).isTrue());
    }
}
