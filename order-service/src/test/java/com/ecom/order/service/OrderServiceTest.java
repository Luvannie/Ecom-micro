package com.ecom.order.service;

import com.ecom.order.client.CartClient;
import com.ecom.order.client.CartItemResponse;
import com.ecom.order.client.CartResponse;
import com.ecom.order.client.InventoryClient;
import com.ecom.order.domain.Order;
import com.ecom.order.domain.OrderStatus;
import com.ecom.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CartClient cartClient;

    @Mock
    private InventoryClient inventoryClient;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, cartClient, inventoryClient);
    }

    @Test
    void createOrderFromCartStoresSnapshots() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(cartClient.getCart(userId, "customer@example.com")).thenReturn(new CartResponse(userId, List.of(
                new CartItemResponse(productId, "Margherita", new BigDecimal("12.50"), 2, null,
                        new BigDecimal("25.00"))), new BigDecimal("25.00"), Instant.now()));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = orderService.createOrder(userId, "customer@example.com");

        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.subtotal()).isEqualByComparingTo("25.00");
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.productId()).isEqualTo(productId);
            assertThat(item.productName()).isEqualTo("Margherita");
            assertThat(item.unitPrice()).isEqualByComparingTo("12.50");
            assertThat(item.quantity()).isEqualTo(2);
            assertThat(item.subtotal()).isEqualByComparingTo("25.00");
        });
        verify(cartClient).clearCart(userId, "customer@example.com");
    }

    @Test
    void emptyCartRejectsCreateOrder() {
        UUID userId = UUID.randomUUID();
        when(cartClient.getCart(userId, "customer@example.com"))
                .thenReturn(new CartResponse(userId, List.of(), BigDecimal.ZERO, Instant.now()));

        assertThatThrownBy(() -> orderService.createOrder(userId, "customer@example.com"))
                .isInstanceOf(EmptyCartException.class);
        verifyNoInteractions(inventoryClient);
    }

    @Test
    void getOrderRejectsAccessByAnotherUser() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findByIdAndUserId(orderId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(userId, orderId))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void cancelReservedOrderCallsInventoryRelease() {
        UUID userId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        Order order = order(userId);
        order.markReserved(reservationId);
        when(orderRepository.findByIdAndUserId(order.getId(), userId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = orderService.cancelOrder(userId, order.getId());

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        verify(inventoryClient).release(reservationId);
    }

    @Test
    void reservationFailedChangesStatusToFailed() {
        UUID userId = UUID.randomUUID();
        Order order = order(userId);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        orderService.markReservationFailed(order.getId(), "insufficient stock");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
    }

    @Test
    void listOrdersReturnsOnlyCurrentUserOrders() {
        UUID userId = UUID.randomUUID();
        Order order = order(userId);
        when(orderRepository.findByUserId(userId, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(order)));

        var page = orderService.listOrders(userId, PageRequest.of(0, 10));

        assertThat(page.getContent()).singleElement()
                .satisfies(summary -> assertThat(summary.id()).isEqualTo(order.getId()));
    }

    private Order order(UUID userId) {
        Order order = new Order(userId);
        order.addItem(UUID.randomUUID(), "Margherita", new BigDecimal("12.50"), 2);
        return order;
    }
}
