package com.ecom.inventory.web;

import com.ecom.inventory.messaging.InventoryEventProducer;
import com.ecom.inventory.repository.ProductStockRepository;
import com.ecom.inventory.repository.StockAuditLogRepository;
import com.ecom.inventory.repository.StockReservationRepository;
import com.ecom.inventory.service.InventoryService;
import com.ecom.inventory.web.dto.SetStockRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryControllerIT {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private ProductStockRepository productStockRepository;

    @Autowired
    private StockReservationRepository reservationRepository;

    @Autowired
    private StockAuditLogRepository auditLogRepository;

    @MockBean
    private InventoryEventProducer eventProducer;

    @BeforeEach
    void resetState() {
        reservationRepository.deleteAll();
        auditLogRepository.deleteAll();
        productStockRepository.deleteAll();
    }

    @Test
    void adminSetStockRequiresAdminRole() throws Exception {
        UUID productId = UUID.randomUUID();
        String body = objectMapper.writeValueAsString(new SetStockRequest(7, "load"));

        mockMvc.perform(put("/api/admin/inventory/products/{productId}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/admin/inventory/products/{productId}", productId)
                        .header("X-User-Roles", "USER,ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(7))
                .andExpect(jsonPath("$.reservedQuantity").value(0));
    }

    @Test
    void reservationEndpointReturnsReservationId() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        inventoryService.setStock(productId, new SetStockRequest(10, "load"));

        mockMvc.perform(post("/api/inventory/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "orderId", orderId,
                                "items", List.of(Map.of("productId", productId, "quantity", 3))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservationId", notNullValue()))
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("RESERVED"));
    }

    @Test
    void publicAvailabilityReturnsZeroForUnknownProduct() throws Exception {
        UUID productId = UUID.randomUUID();

        mockMvc.perform(get("/api/inventory/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(productId.toString()))
                .andExpect(jsonPath("$.availableQuantity").value(0))
                .andExpect(jsonPath("$.reservedQuantity").value(0));
    }
}
