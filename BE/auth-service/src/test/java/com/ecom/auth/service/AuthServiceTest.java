package com.ecom.auth.service;

import com.ecom.auth.repository.RefreshTokenRepository;
import com.ecom.auth.repository.UserCredentialRepository;
import com.ecom.auth.web.dto.LoginRequest;
import com.ecom.auth.web.dto.RefreshRequest;
import com.ecom.auth.web.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class AuthServiceTest {
    @Autowired
    private AuthService authService;

    @Autowired
    private UserCredentialRepository users;

    @Autowired
    private RefreshTokenRepository refreshTokens;

    @Test
    void registerCreatesCustomerCredentialAndTokens() {
        String email = email();

        AuthResult result = authService.register(new RegisterRequest(email.toUpperCase(), "Password123!", "Demo Customer"));

        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(users.findByEmail(email)).hasValueSatisfying(user -> {
            assertThat(user.isEnabled()).isTrue();
            assertThat(user.roleNames()).containsExactly("CUSTOMER");
        });
    }

    @Test
    void duplicateRegisterFails() {
        String email = email();
        authService.register(new RegisterRequest(email, "Password123!", "Demo"));

        assertThatThrownBy(() -> authService.register(new RegisterRequest(email, "Password123!", "Demo")))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void refreshRotatesRefreshToken() {
        AuthResult registered = authService.register(new RegisterRequest(email(), "Password123!", "Demo"));

        AuthResult refreshed = authService.refresh(new RefreshRequest(registered.refreshToken()));

        assertThat(refreshed.refreshToken()).isNotEqualTo(registered.refreshToken());
        assertThat(refreshTokens.findByTokenHash(AuthService.sha256(registered.refreshToken())))
                .hasValueSatisfying(token -> assertThat(token.isRevoked()).isTrue());
    }

    @Test
    void loginReturnsAccessAndRefreshTokens() {
        String email = email();
        authService.register(new RegisterRequest(email, "Password123!", "Demo"));

        AuthResult result = authService.login(new LoginRequest(email, "Password123!"));

        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.expiresInSeconds()).isEqualTo(900);
    }

    @Test
    void logoutRevokesRefreshToken() {
        AuthResult registered = authService.register(new RegisterRequest(email(), "Password123!", "Demo"));

        authService.logout(new com.ecom.auth.web.dto.LogoutRequest(registered.refreshToken()));

        assertThat(refreshTokens.findByTokenHash(AuthService.sha256(registered.refreshToken())))
                .hasValueSatisfying(token -> assertThat(token.isRevoked()).isTrue());
    }

    @Test
    void loginRejectsInvalidPassword() {
        String email = email();
        authService.register(new RegisterRequest(email, "Password123!", "Demo"));

        assertThatThrownBy(() -> authService.login(new LoginRequest(email, "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    private String email() {
        return "auth-service-" + UUID.randomUUID() + "@example.com";
    }
}
