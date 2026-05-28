package com.ecom.auth.web.dto;

import java.util.Set;
import java.util.UUID;

public record CurrentUserResponse(UUID userId, String email, Set<String> roles) {
}
