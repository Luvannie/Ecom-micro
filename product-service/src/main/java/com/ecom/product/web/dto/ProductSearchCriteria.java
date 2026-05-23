package com.ecom.product.web.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductSearchCriteria(String keyword, UUID categoryId, BigDecimal minPrice, BigDecimal maxPrice) {
    public static ProductSearchCriteria empty() {
        return new ProductSearchCriteria(null, null, null, null);
    }

    public String toCacheKey() {
        return "keyword=%s:category=%s:min=%s:max=%s".formatted(
                normalize(keyword), categoryId, minPrice, maxPrice);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
