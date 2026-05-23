package com.ecom.order.messaging;

import com.ecom.order.web.dto.OrderResponse;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

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
        kafkaTemplate.send(OrderTopics.INVENTORY_RESERVATION_REQUESTED, order.id().toString(), event);
    }

    public void publishOrderCancelled(OrderResponse order) {
        kafkaTemplate.send(OrderTopics.ORDER_CANCELLED, order.id().toString(),
                new OrderCancelledEvent(order.id(), order.userId(), order.reservationId()));
    }

    public void publishOrderConfirmed(OrderResponse order) {
        kafkaTemplate.send(OrderTopics.ORDER_CONFIRMED, order.id().toString(),
                new OrderConfirmedEvent(order.id(), order.userId(), order.total()));
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
