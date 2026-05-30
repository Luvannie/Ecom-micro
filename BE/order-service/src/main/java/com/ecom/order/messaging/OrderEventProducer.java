package com.ecom.order.messaging;

import com.ecom.order.web.dto.OrderResponse;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class OrderEventProducer {
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    public OrderEventProducer(KafkaTemplate<Object, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishReservationRequested(OrderResponse order) {
        var event = new ReservationRequestEvent(order.id(), order.items().stream()
                .map(item -> new ReservationItemEvent(item.productId(), item.quantity()))
                .toList());
        try {
            kafkaTemplate.send(OrderTopics.INVENTORY_RESERVATION_REQUESTED, order.id().toString(), event)
                    .get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish INVENTORY_RESERVATION_REQUESTED for order " + order.id(), e);
        }
    }

    public void publishOrderCancelled(OrderResponse order) {
        try {
            kafkaTemplate.send(OrderTopics.ORDER_CANCELLED, order.id().toString(),
                    new OrderCancelledEvent(order.id(), order.userId(), order.reservationId()))
                    .get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish ORDER_CANCELLED for order " + order.id(), e);
        }
    }

    public void publishOrderConfirmed(OrderResponse order) {
        try {
            kafkaTemplate.send(OrderTopics.ORDER_CONFIRMED, order.id().toString(),
                    new OrderConfirmedEvent(order.id(), order.userId(), order.total()))
                    .get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish ORDER_CONFIRMED for order " + order.id(), e);
        }
    }

    public record ReservationRequestEvent(UUID orderId, List<ReservationItemEvent> items) {
    }

    public record ReservationItemEvent(UUID productId, int quantity) {
    }

    public record OrderCancelledEvent(UUID orderId, UUID userId, UUID reservationId) {
    }

    public record OrderConfirmedEvent(UUID orderId, UUID userId, java.math.BigDecimal total) {
    }
}