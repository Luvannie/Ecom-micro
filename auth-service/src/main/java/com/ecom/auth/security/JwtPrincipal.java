package com.ecom.auth.security;

import java.util.Set;
import java.util.UUID;

public record JwtPrincipal(UUID userId, String email, Set<String> roles) {
}
