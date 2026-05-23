package com.ecom.product.web.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductSummaryResponse(
        UUID id,
        UUID categoryId,
        String name,
        String slug,
        BigDecimal price,
        String imageUrl,
        String promotionTag) {
}
