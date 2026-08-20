package com.ecom.product.web;

import com.ecom.product.domain.Category;
import com.ecom.product.domain.Product;
import com.ecom.product.service.ProductCatalogService;
import com.ecom.product.web.dto.CategoryResponse;
import com.ecom.product.web.dto.ProductResponse;
import com.ecom.product.web.dto.ProductSearchCriteria;
import com.ecom.product.web.dto.ProductSummaryResponse;
import com.ecom.product.web.mapper.ProductMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
public class ProductController {
    private final ProductCatalogService catalogService;
    private final ProductMapper productMapper;

    public ProductController(ProductCatalogService catalogService, ProductMapper productMapper) {
        this.catalogService = catalogService;
        this.productMapper = productMapper;
    }

    @GetMapping("/api/categories")
    public List<CategoryResponse> categories() {
        return catalogService.listActiveCategories().stream()
                .map(productMapper::toCategoryResponse)
                .toList();
    }

    @GetMapping("/api/products")
    public Page<ProductSummaryResponse> products(@RequestParam(name = "keyword", required = false) String keyword,
                                                 @RequestParam(name = "categoryId", required = false) UUID categoryId,
                                                 @RequestParam(name = "minPrice", required = false) BigDecimal minPrice,
                                                 @RequestParam(name = "maxPrice", required = false) BigDecimal maxPrice,
                                                 @PageableDefault(size = 20) Pageable pageable) {
        return catalogService.search(new ProductSearchCriteria(keyword, categoryId, minPrice, maxPrice), pageable)
                .map(productMapper::toProductSummary);
    }

    @GetMapping("/api/products/{productId}")
    public ProductResponse product(@PathVariable("productId") UUID productId) {
        return catalogService.getProduct(productId);
    }
}
