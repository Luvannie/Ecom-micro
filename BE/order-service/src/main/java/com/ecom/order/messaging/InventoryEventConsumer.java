package com.ecom.order.messaging;

import com.ecom.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class InventoryEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(InventoryEventConsumer.class);
    private final OrderService orderService;

    public InventoryEventConsumer(OrderService orderService) {
        this.orderService = orderService;
    }

    @KafkaListener(
        topics = OrderTopics.INVENTORY_RESERVED,
        groupId = "${spring.application.name}",
        concurrency = "3",
        errorHandler = "kafkaErrorHandler"
    )
    public void handleReserved(ReservationResult event) {
        log.info("Processing INVENTORY_RESERVED for orderId={}, reservationId={}",
                event.orderId(), event.reservationId());
        try {
            orderService.markReserved(event.orderId(), event.reservationId());
            log.info("Successfully processed INVENTORY_RESERVED for orderId={}", event.orderId());
        } catch (Exception e) {
            log.error("Failed to process INVENTORY_RESERVED for orderId={}: {}",
                    event.orderId(), e.getMessage());
            throw e;
        }
    }

    @KafkaListener(
        topics = OrderTopics.INVENTORY_RESERVATION_FAILED,
        groupId = "${spring.application.name}",
        concurrency = "3",
        errorHandler = "kafkaErrorHandler"
    )
    public void handleReservationFailed(ReservationResult event) {
        log.info("Processing INVENTORY_RESERVATION_FAILED for orderId={}", event.orderId());
        try {
            orderService.markReservationFailed(event.orderId(), event.failureReason());
            log.info("Successfully processed INVENTORY_RESERVATION_FAILED for orderId={}",
                    event.orderId());
        } catch (Exception e) {
            log.error("Failed to process INVENTORY_RESERVATION_FAILED for orderId={}: {}",
                    event.orderId(), e.getMessage());
            throw e;
        }
    }

    public record ReservationResult(UUID reservationId, UUID orderId, String status, String failureReason) {
    }
}