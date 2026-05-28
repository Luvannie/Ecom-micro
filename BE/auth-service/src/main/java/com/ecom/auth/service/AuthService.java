package com.ecom.auth.service;

import com.ecom.auth.domain.RefreshToken;
import com.ecom.auth.domain.UserCredential;
import com.ecom.auth.repository.RefreshTokenRepository;
import com.ecom.auth.repository.UserCredentialRepository;
import com.ecom.auth.security.JwtProperties;
import com.ecom.auth.security.JwtTokenService;
import com.ecom.auth.web.dto.CurrentUserResponse;
import com.ecom.auth.web.dto.LoginRequest;
import com.ecom.auth.web.dto.LogoutRequest;
import com.ecom.auth.web.dto.RefreshRequest;
import com.ecom.auth.web.dto.RegisterRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AuthService {
    private final UserCredentialRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    @Autowired
    public AuthService(UserCredentialRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
                       JwtTokenService jwtTokenService, JwtProperties jwtProperties) {
        this(users, refreshTokens, passwordEncoder, jwtTokenService, jwtProperties, Clock.systemUTC());
    }

    public AuthService(UserCredentialRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
                       JwtTokenService jwtTokenService, JwtProperties jwtProperties, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    @Transactional
    public AuthResult register(RegisterRequest request) {
        String email = UserCredential.normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new DuplicateEmailException("Email already registered");
        }
        UserCredential credential = users.save(UserCredential.create(email, passwordEncoder.encode(request.password())));
        return issueTokens(credential);
    }

    @Transactional
    public AuthResult login(LoginRequest request) {
        UserCredential credential = users.findByEmail(UserCredential.normalizeEmail(request.email()))
                .filter(user -> passwordEncoder.matches(request.password(), user.getPasswordHash()))
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));
        return issueTokens(credential);
    }

    @Transactional
    public AuthResult refresh(RefreshRequest request) {
        RefreshToken existing = refreshTokens.findByTokenHash(sha256(request.refreshToken()))
                .filter(token -> !token.isRevoked() && !token.isExpired(clock.instant()))
                .orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));
        existing.revoke(clock.instant());
        UserCredential credential = users.findById(existing.getUserId())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));
        return issueTokens(credential);
    }

    @Transactional
    public void logout(LogoutRequest request) {
        refreshTokens.findByTokenHash(sha256(request.refreshToken()))
                .ifPresent(token -> token.revoke(clock.instant()));
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse me(UUID userId) {
        UserCredential credential = users.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("User not found"));
        return new CurrentUserResponse(credential.getId(), credential.getEmail(), credential.roleNames());
    }

    public static String sha256(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private AuthResult issueTokens(UserCredential credential) {
        String accessToken = jwtTokenService.createAccessToken(credential.getId(), credential.getEmail(), credential.getRoles());
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        Instant now = clock.instant();
        refreshTokens.save(new RefreshToken(credential.getId(), sha256(refreshToken), now.plus(jwtProperties.getRefreshTokenTtlDays(), ChronoUnit.DAYS), now));
        return new AuthResult(accessToken, refreshToken, jwtProperties.getAccessTokenTtlMinutes() * 60);
    }
}
