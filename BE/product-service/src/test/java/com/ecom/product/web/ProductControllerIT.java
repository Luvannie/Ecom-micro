package com.ecom.product.web;

import com.ecom.product.repository.CategoryRepository;
import com.ecom.product.repository.ProductRepository;
import com.ecom.product.service.ProductCatalogService;
import com.ecom.product.web.dto.CreateCategoryRequest;
import com.ecom.product.web.dto.CreateProductRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductControllerIT {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductCatalogService catalogService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @BeforeEach
    void resetState() {
        productRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    @Test
    void publicCategoryListReturnsActiveCategories() throws Exception {
        catalogService.createCategory(new CreateCategoryRequest("Pizza", "pizza"));

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("pizza"));
    }

    @Test
    void publicProductSearchSupportsKeywordAndCategoryFilter() throws Exception {
        var pizza = catalogService.createCategory(new CreateCategoryRequest("Pizza", "pizza"));
        var drinks = catalogService.createCategory(new CreateCategoryRequest("Drinks", "drinks"));
        catalogService.createProduct(product(pizza.id(), "Margherita Pizza", "margherita"));
        catalogService.createProduct(product(drinks.id(), "Pizza Soda", "pizza-soda"));

        mockMvc.perform(get("/api/products")
                        .param("keyword", "pizza")
                        .param("categoryId", pizza.id().toString())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Margherita Pizza"))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void publicProductDetailReturnsNotFoundForInactiveProduct() throws Exception {
        var category = catalogService.createCategory(new CreateCategoryRequest("Sides", "sides"));
        var product = catalogService.createProduct(product(category.id(), "Fries", "fries"));
        catalogService.changeStatus(product.id(), false);

        mockMvc.perform(get("/api/products/{productId}", product.id()))
                .andExpect(status().isNotFound());
    }

    private CreateProductRequest product(UUID categoryId, String name, String slug) {
        return new CreateProductRequest(categoryId, name, slug, "Demo", new BigDecimal("9.99"), null, null);
    }
}
