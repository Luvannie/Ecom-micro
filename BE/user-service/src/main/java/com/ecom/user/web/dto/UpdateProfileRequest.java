package com.ecom.user.web.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateProfileRequest(@NotBlank String displayName, String phone, String preferences) {
}
