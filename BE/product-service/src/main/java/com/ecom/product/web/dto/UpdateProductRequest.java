package com.ecom.product.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record UpdateProductRequest(
        @NotNull UUID categoryId,
        @NotBlank @Size(max = 180) String name,
        @NotBlank @Size(max = 220) String slug,
        @NotBlank String description,
        @NotNull @DecimalMin("0.01") BigDecimal price,
        @Size(max = 500) String imageUrl,
        @Size(max = 80) String promotionTag) {
}
