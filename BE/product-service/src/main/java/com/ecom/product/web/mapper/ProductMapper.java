package com.ecom.product.web.mapper;

import com.ecom.product.domain.Category;
import com.ecom.product.domain.Product;
import com.ecom.product.web.dto.CategoryResponse;
import com.ecom.product.web.dto.ProductResponse;
import com.ecom.product.web.dto.ProductSummaryResponse;
import org.springframework.stereotype.Component;

/**
 * Maps domain entities to web-layer DTOs. Lives in the web package so
 * service code does not depend on DTO classes.
 */
@Component
public class ProductMapper {

    public CategoryResponse toCategoryResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getName(),
                category.getSlug(), category.isActive());
    }

    public ProductResponse toProductResponse(Product product) {
        return new ProductResponse(product.getId(),
                product.getCategory().getId(), product.getCategory().getName(),
                product.getName(), product.getSlug(), product.getDescription(),
                product.getPrice(), product.getImageUrl(),
                product.isActive(), product.getPromotionTag());
    }

    public ProductSummaryResponse toProductSummary(Product product) {
        return new ProductSummaryResponse(product.getId(), product.getCategory().getId(),
                product.getName(), product.getSlug(), product.getPrice(),
                product.getImageUrl(), product.getPromotionTag());
    }
}
