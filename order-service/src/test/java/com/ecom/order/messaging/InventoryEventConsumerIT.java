package com.ecom.order.messaging;

import com.ecom.order.client.CartClient;
import com.ecom.order.client.InventoryClient;
import com.ecom.order.domain.Order;
import com.ecom.order.domain.OrderStatus;
import com.ecom.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class InventoryEventConsumerIT {
    @Autowired
    private InventoryEventConsumer consumer;

    @Autowired
    private OrderRepository orderRepository;

    @MockBean
    private CartClient cartClient;

    @MockBean
    private InventoryClient inventoryClient;

    @MockBean
    private OrderEventProducer eventProducer;

    @BeforeEach
    void resetState() {
        orderRepository.deleteAll();
    }

    @Test
    void inventorySuccessEventUpdatesOrderStatus() {
        Order order = order();
        orderRepository.save(order);
        UUID reservationId = UUID.randomUUID();

        consumer.handleReserved(new InventoryEventConsumer.ReservationResult(reservationId, order.getId(), "RESERVED", null));

        assertThat(orderRepository.findById(order.getId())).get().satisfies(saved -> {
            assertThat(saved.getStatus()).isEqualTo(OrderStatus.RESERVED);
            assertThat(saved.getReservationId()).isEqualTo(reservationId);
        });
    }

    @Test
    void inventoryFailureEventUpdatesOrderStatus() {
        Order order = order();
        orderRepository.save(order);

        consumer.handleReservationFailed(new InventoryEventConsumer.ReservationResult(
                UUID.randomUUID(), order.getId(), "FAILED", "insufficient stock"));

        assertThat(orderRepository.findById(order.getId())).get()
                .extracting("status")
                .isEqualTo(OrderStatus.FAILED);
    }

    private Order order() {
        Order order = new Order(UUID.randomUUID());
        order.addItem(UUID.randomUUID(), "Margherita", new BigDecimal("12.50"), 2);
        return order;
    }
}
