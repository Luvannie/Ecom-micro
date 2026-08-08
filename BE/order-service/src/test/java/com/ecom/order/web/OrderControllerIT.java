package com.ecom.order.web;

import com.ecom.order.domain.Order;
import com.ecom.order.messaging.OrderEventProducer;
import com.ecom.order.port.CartQueryPort;
import com.ecom.order.port.InventoryCommandPort;
import com.ecom.order.port.dto.CartItemView;
import com.ecom.order.port.dto.CartView;
import com.ecom.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderControllerIT {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @MockBean
    private CartQueryPort cartQueryPort;

    @MockBean
    private InventoryCommandPort inventoryCommandPort;

    @MockBean
    private OrderEventProducer eventProducer;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void resetState() {
        orderRepository.deleteAll();
    }

    @Test
    void createOrderEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createOrderEmitsReservationRequest() throws Exception {
        UUID productId = UUID.randomUUID();
        when(cartQueryPort.getCart(userId, "customer@example.com")).thenReturn(cart(productId));

        mockMvc.perform(post("/api/orders")
                        .header("X-User-Id", userId)
                        .header("X-User-Email", "customer@example.com"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.items", hasSize(1)));

        verify(eventProducer).publishReservationRequested(any());
    }

    @Test
    void listOrdersReturnsOnlyCurrentUserOrders() throws Exception {
        Order currentUserOrder = order(userId);
        orderRepository.save(currentUserOrder);
        orderRepository.save(order(UUID.randomUUID()));

        mockMvc.perform(get("/api/orders")
                        .header("X-User-Id", userId)
                        .header("X-User-Email", "customer@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(currentUserOrder.getId().toString()));
    }

    @Test
    void cancelOrderEmitsOrderCancelled() throws Exception {
        Order order = order(userId);
        orderRepository.save(order);

        mockMvc.perform(post("/api/orders/{orderId}/cancel", order.getId())
                        .header("X-User-Id", userId)
                        .header("X-User-Email", "customer@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(eventProducer).publishOrderCancelled(any());
    }

    private CartView cart(UUID productId) {
        return new CartView(userId, List.of(new CartItemView(productId, "Margherita",
                new BigDecimal("12.50"), 2)));
    }

    private Order order(UUID userId) {
        Order order = new Order(userId);
        order.addItem(UUID.randomUUID(), "Margherita", new BigDecimal("12.50"), 2);
        return order;
    }
}
