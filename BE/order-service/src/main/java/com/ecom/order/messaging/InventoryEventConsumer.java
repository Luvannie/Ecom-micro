package com.ecom.order.messaging;

import com.ecom.common.messaging.KafkaListenerResilienceWrapper;
import com.ecom.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class InventoryEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(InventoryEventConsumer.class);
    private final OrderService orderService;
    private final KafkaListenerResilienceWrapper wrapper;

    public InventoryEventConsumer(OrderService orderService, KafkaListenerResilienceWrapper wrapper) {
        this.orderService = orderService;
        this.wrapper = wrapper;
    }

    @KafkaListener(
        topics = OrderTopics.INVENTORY_RESERVED,
        groupId = "${spring.application.name}",
        concurrency = "3"
    )
    public void handleReserved(ReservationResult event) {
        log.info("Processing INVENTORY_RESERVED for orderId={}, reservationId={}",
                event.orderId(), event.reservationId());
        wrapper.execute(
            () -> {
                orderService.markReserved(event.orderId(), event.reservationId());
                log.info("Successfully processed INVENTORY_RESERVED for orderId={}", event.orderId());
                return null;
            },
            ex -> log.warn("Timeout/breaker open for order.inventory-reserved (orderId={}): {}",
                           event.orderId(), ex.getMessage())
        );
    }

    @KafkaListener(
        topics = OrderTopics.INVENTORY_RESERVATION_FAILED,
        groupId = "${spring.application.name}",
        concurrency = "3"
    )
    public void handleReservationFailed(ReservationResult event) {
        log.info("Processing INVENTORY_RESERVATION_FAILED for orderId={}", event.orderId());
        wrapper.execute(
            () -> {
                orderService.markReservationFailed(event.orderId(), event.failureReason());
                log.info("Successfully processed INVENTORY_RESERVATION_FAILED for orderId={}",
                        event.orderId());
                return null;
            },
            ex -> log.warn("Timeout/breaker open for order.inventory-reservation-failed (orderId={}): {}",
                           event.orderId(), ex.getMessage())
        );
    }

    public record ReservationResult(UUID reservationId, UUID orderId, String status, String failureReason) {
    }
}
