package com.ecom.user.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateAddressRequest(
        @NotBlank String recipientName,
        @NotBlank String phone,
        @NotBlank String line1,
        String line2,
        @NotBlank String city,
        @NotBlank String district,
        String postalCode,
        boolean defaultAddress
) {
}
