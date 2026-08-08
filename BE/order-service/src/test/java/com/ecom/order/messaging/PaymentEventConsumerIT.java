package com.ecom.order.messaging;

import com.ecom.order.domain.Order;
import com.ecom.order.domain.OrderStatus;
import com.ecom.order.port.CartQueryPort;
import com.ecom.order.port.InventoryCommandPort;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class PaymentEventConsumerIT {
    @Autowired
    private PaymentEventConsumer consumer;

    @Autowired
    private OrderRepository orderRepository;

    @MockBean
    private CartQueryPort cartQueryPort;

    @MockBean
    private InventoryCommandPort inventoryCommandPort;

    @MockBean
    private OrderEventProducer eventProducer;

    @BeforeEach
    void resetState() {
        orderRepository.deleteAll();
    }

    @Test
    void paymentSuccessConfirmsOrderAndEmitsOrderConfirmed() {
        Order order = reservedOrder();
        orderRepository.save(order);

        consumer.handleSucceeded(new PaymentEventConsumer.PaymentEvent(
                UUID.randomUUID(), order.getId(), order.getUserId(), new BigDecimal("25.00"), "USD", "SUCCEEDED"));

        assertThat(orderRepository.findById(order.getId())).get()
                .extracting("status")
                .isEqualTo(OrderStatus.CONFIRMED);
        verify(eventProducer).publishOrderConfirmed(any());
    }

    @Test
    void paymentFailureCancelsOrderAndEmitsOrderCancelled() {
        Order order = reservedOrder();
        orderRepository.save(order);

        consumer.handleFailed(new PaymentEventConsumer.PaymentEvent(
                UUID.randomUUID(), order.getId(), order.getUserId(), new BigDecimal("25.00"), "USD", "FAILED"));

        assertThat(orderRepository.findById(order.getId())).get()
                .extracting("status")
                .isEqualTo(OrderStatus.CANCELLED);
        verify(eventProducer).publishOrderCancelled(any());
    }

    private Order reservedOrder() {
        Order order = new Order(UUID.randomUUID());
        order.addItem(UUID.randomUUID(), "Margherita", new BigDecimal("12.50"), 2);
        order.markReserved(UUID.randomUUID());
        return order;
    }
}
