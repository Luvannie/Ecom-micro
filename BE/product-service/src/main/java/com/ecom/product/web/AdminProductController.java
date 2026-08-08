package com.ecom.product.web;

import com.ecom.product.service.ProductCatalogService;
import com.ecom.product.web.dto.CategoryResponse;
import com.ecom.product.web.dto.CreateCategoryRequest;
import com.ecom.product.web.dto.CreateProductRequest;
import com.ecom.product.web.dto.ProductResponse;
import com.ecom.product.web.dto.ProductStatusRequest;
import com.ecom.product.web.dto.UpdateProductRequest;
import com.ecom.product.web.mapper.ProductMapper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
public class AdminProductController {
    private final ProductCatalogService catalogService;
    private final ProductMapper productMapper;

    public AdminProductController(ProductCatalogService catalogService, ProductMapper productMapper) {
        this.catalogService = catalogService;
        this.productMapper = productMapper;
    }

    @PostMapping("/api/admin/categories")
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        CategoryResponse response = productMapper.toCategoryResponse(catalogService.createCategory(request));
        return ResponseEntity.created(URI.create("/api/admin/categories/" + response.id())).body(response);
    }

    @PostMapping("/api/admin/products")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody CreateProductRequest request) {
        ProductResponse response = productMapper.toProductResponse(catalogService.createProduct(request));
        return ResponseEntity.created(URI.create("/api/admin/products/" + response.id())).body(response);
    }

    @PutMapping("/api/admin/products/{productId}")
    public ProductResponse updateProduct(@PathVariable("productId") UUID productId, @Valid @RequestBody UpdateProductRequest request) {
        return productMapper.toProductResponse(catalogService.updateProduct(productId, request));
    }

    @PatchMapping("/api/admin/products/{productId}/status")
    public ProductResponse changeStatus(@PathVariable("productId") UUID productId, @Valid @RequestBody ProductStatusRequest request) {
        return productMapper.toProductResponse(catalogService.changeStatus(productId, request.active()));
    }
}
