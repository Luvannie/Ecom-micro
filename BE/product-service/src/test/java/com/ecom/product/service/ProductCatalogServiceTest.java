package com.ecom.product.service;

import com.ecom.product.repository.CategoryRepository;
import com.ecom.product.repository.ProductRepository;
import com.ecom.product.web.dto.CreateCategoryRequest;
import com.ecom.product.web.dto.CreateProductRequest;
import com.ecom.product.web.dto.ProductSearchCriteria;
import com.ecom.product.web.dto.UpdateProductRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class ProductCatalogServiceTest {
    @Autowired
    private ProductCatalogService catalogService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository realProductRepository;

    @SpyBean
    private ProductRepository productRepository;

    @BeforeEach
    void resetState() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        realProductRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    @Test
    void createCategoryRejectsDuplicateSlug() {
        catalogService.createCategory(new CreateCategoryRequest("Pizza", "pizza"));

        assertThatThrownBy(() -> catalogService.createCategory(new CreateCategoryRequest("Pizza 2", "pizza")))
                .isInstanceOf(DuplicateSlugException.class);
    }

    @Test
    void createProductRequiresExistingCategory() {
        assertThatThrownBy(() -> catalogService.createProduct(product(UUID.randomUUID(), "Margherita", "margherita")))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    void searchReturnsOnlyActiveProducts() {
        var category = catalogService.createCategory(new CreateCategoryRequest("Pizza", "pizza"));
        catalogService.createProduct(product(category.getId(), "Margherita", "margherita"));
        var inactive = catalogService.createProduct(product(category.getId(), "Inactive Pizza", "inactive-pizza"));
        catalogService.changeStatus(inactive.getId(), false);

        var results = catalogService.search(ProductSearchCriteria.empty(), PageRequest.of(0, 10));

        assertThat(results.getContent())
                .extracting("name")
                .containsExactly("Margherita");
    }

    @Test
    void inactiveProductDetailReturnsNotFound() {
        var category = catalogService.createCategory(new CreateCategoryRequest("Drinks", "drinks"));
        var product = catalogService.createProduct(product(category.getId(), "Cola", "cola"));
        catalogService.changeStatus(product.getId(), false);

        assertThatThrownBy(() -> catalogService.getProduct(product.getId()))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void updateProductEvictsCachedDetail() {
        var category = catalogService.createCategory(new CreateCategoryRequest("Sides", "sides"));
        var product = catalogService.createProduct(product(category.getId(), "Fries", "fries"));

        assertThat(catalogService.getProduct(product.getId()).getName()).isEqualTo("Fries");
        assertThat(catalogService.getProduct(product.getId()).getName()).isEqualTo("Fries");

        catalogService.updateProduct(product.getId(), new UpdateProductRequest(
                category.getId(), "Curly Fries", "curly-fries", "Crispy", new BigDecimal("4.50"), null, null));

        assertThat(catalogService.getProduct(product.getId()).getName()).isEqualTo("Curly Fries");
        verify(productRepository, atLeast(2)).findByIdAndActiveTrue(product.getId());
    }

    private CreateProductRequest product(UUID categoryId, String name, String slug) {
        return new CreateProductRequest(
                categoryId,
                name,
                slug,
                "Tasty product",
                new BigDecimal("12.50"),
                "https://example.com/image.jpg",
                null);
    }
}
