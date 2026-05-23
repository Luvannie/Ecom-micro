package com.ecom.auth.web.dto;

import com.ecom.auth.service.AuthResult;

public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresInSeconds) {
    public static TokenResponse from(AuthResult result) {
        return new TokenResponse(result.accessToken(), result.refreshToken(), "Bearer", result.expiresInSeconds());
    }
}
