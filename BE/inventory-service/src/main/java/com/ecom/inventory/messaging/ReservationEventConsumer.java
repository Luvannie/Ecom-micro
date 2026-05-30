package com.ecom.inventory.messaging;

import com.ecom.inventory.domain.ReservationStatus;
import com.ecom.inventory.service.InventoryService;
import com.ecom.inventory.web.dto.ReservationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ReservationEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(ReservationEventConsumer.class);
    private final InventoryService inventoryService;
    private final InventoryEventProducer eventProducer;

    public ReservationEventConsumer(InventoryService inventoryService, InventoryEventProducer eventProducer) {
        this.inventoryService = inventoryService;
        this.eventProducer = eventProducer;
    }

    @KafkaListener(
        topics = InventoryTopics.RESERVATION_REQUESTED,
        groupId = "${spring.application.name}",
        concurrency = "3"
    )
    public void handleReservationRequested(ReservationRequest request) {
        log.info("Processing RESERVATION_REQUESTED for orderId={}, itemCount={}",
                request.orderId(), request.items().size());
        try {
            var result = inventoryService.reserve(request);
            if (result.status() == ReservationStatus.RESERVED) {
                log.info("Reservation SUCCESS for orderId={}, reservationId={}",
                        request.orderId(), result.reservationId());
                eventProducer.publishReserved(result);
            } else {
                log.warn("Reservation FAILED for orderId={}: {}",
                        request.orderId(), result.failureReason());
                eventProducer.publishReservationFailed(result);
            }
        } catch (Exception e) {
            log.error("Failed to process RESERVATION_REQUESTED for orderId={}: {}",
                    request.orderId(), e.getMessage());
            throw e;
        }
    }
}