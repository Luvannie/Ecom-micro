package com.ecom.product.web.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        UUID categoryId,
        String categoryName,
        String name,
        String slug,
        String description,
        BigDecimal price,
        String imageUrl,
        boolean active,
        String promotionTag) {
}
