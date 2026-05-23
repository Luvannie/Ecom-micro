package com.ecom.auth.service;

public record AuthResult(String accessToken, String refreshToken, long expiresInSeconds) {
}
