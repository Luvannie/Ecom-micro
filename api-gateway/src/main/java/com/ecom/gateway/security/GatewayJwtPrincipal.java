package com.ecom.gateway.security;

import java.util.List;
import java.util.UUID;

public record GatewayJwtPrincipal(UUID userId, String email, List<String> roles) {
}
