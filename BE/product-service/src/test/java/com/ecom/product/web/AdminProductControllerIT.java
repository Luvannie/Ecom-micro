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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminProductControllerIT {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    void adminCreateProductWithoutAdminRoleReturnsForbidden() throws Exception {
        var category = catalogService.createCategory(new CreateCategoryRequest("Pizza", "pizza"));

        mockMvc.perform(post("/api/admin/products")
                        .header("X-User-Roles", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateProductRequest(
                                category.getId(), "Margherita", "margherita", "Demo", new BigDecimal("11.50"), null, null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCreateProductWithAdminRoleReturnsCreated() throws Exception {
        var category = catalogService.createCategory(new CreateCategoryRequest("Pizza", "pizza"));

        mockMvc.perform(post("/api/admin/products")
                        .header("X-User-Roles", "CUSTOMER,ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateProductRequest(
                                category.getId(), "Margherita", "margherita", "Demo", new BigDecimal("11.50"), null, null))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/admin/products/")))
                .andExpect(jsonPath("$.name").value("Margherita"));
    }
}
