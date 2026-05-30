package com.ecom.order.messaging;

import com.ecom.order.service.OrderService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class InventoryEventConsumer {
    private final OrderService orderService;

    public InventoryEventConsumer(OrderService orderService) {
        this.orderService = orderService;
    }

    @KafkaListener(topics = OrderTopics.INVENTORY_RESERVED, groupId = "${spring.application.name}")
    public void handleReserved(ReservationResult event) {
        orderService.markReserved(event.orderId(), event.reservationId());
    }

    @KafkaListener(topics = OrderTopics.INVENTORY_RESERVATION_FAILED, groupId = "${spring.application.name}")
    public void handleReservationFailed(ReservationResult event) {
        orderService.markReservationFailed(event.orderId(), event.failureReason());
    }

    public record ReservationResult(UUID reservationId, UUID orderId, String status, String failureReason) {
    }
}
