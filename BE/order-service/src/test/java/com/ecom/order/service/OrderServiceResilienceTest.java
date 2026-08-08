package com.ecom.order.service;

import com.ecom.common.web.ServiceUnavailableException;
import com.ecom.order.domain.Order;
import com.ecom.order.port.CartQueryPort;
import com.ecom.order.port.InventoryCommandPort;
import com.ecom.order.port.dto.CartView;
import com.ecom.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Resilience4J integration test for OrderService. Verifies that:
 * <ul>
 *   <li>When cart-service port call fails with an IOException, the retry policy retries
 *       maxAttempts times before the fallback fires and throws ServiceUnavailableException
 *       with downstream = "cart-service".</li>
 *   <li>When inventory-service port call fails inside cancelOrder, the fallback fires
 *       and throws ServiceUnavailableException with downstream = "inventory-service".</li>
 * </ul>
 *
 * <p>The fallback only fires when Spring AOP is active (i.e., the bean is proxied by the
 * Resilience4J auto-configuration), so this test uses {@code @SpringBootTest} to load
 * the real application context.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class OrderServiceResilienceTest {

    @Autowired
    private OrderService orderService;

    @MockBean
    private CartQueryPort cartQueryPort;

    @MockBean
    private InventoryCommandPort inventoryCommandPort;

    @MockBean
    private OrderRepository orderRepository;

    @Test
    void cartServiceDown_throwsServiceUnavailable() {
        UUID userId = UUID.randomUUID();
        when(cartQueryPort.getCart(any(), any()))
            .thenAnswer(inv -> { throw new IOException("connection refused"); });

        // R4J retry: 3 attempts with 200ms exp backoff
        assertThatThrownBy(() -> orderService.createOrder(userId, "test@example.com"))
            .isInstanceOf(ServiceUnavailableException.class)
            .hasMessageContaining("cart-service");

        verify(cartQueryPort, times(3)).getCart(any(), any());
    }

    @Test
    void emptyCartRejectsCreateOrder() {
        UUID userId = UUID.randomUUID();
        when(cartQueryPort.getCart(any(), any())).thenReturn(
            new CartView(userId, List.of()));

        assertThatThrownBy(() -> orderService.createOrder(userId, "test@example.com"))
            .isInstanceOf(EmptyCartException.class);
    }

    @Test
    void inventoryServiceDown_throwsServiceUnavailableOnCancel() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        Order order = orderWithReservation(userId, reservationId);
        when(orderRepository.findByIdAndUserId(orderId, userId)).thenReturn(java.util.Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.doAnswer(inv -> { throw new IOException("inventory down"); })
            .when(inventoryCommandPort).release(any());

        assertThatThrownBy(() -> orderService.cancelOrder(userId, orderId))
            .isInstanceOf(ServiceUnavailableException.class)
            .hasMessageContaining("inventory-service");
    }

    private Order orderWithReservation(UUID userId, UUID reservationId) {
        Order order = new Order(userId);
        order.addItem(UUID.randomUUID(), "Pizza", new BigDecimal("10.00"), 1);
        order.markReserved(reservationId);
        return order;
    }
}
