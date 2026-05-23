package com.ecom.inventory.messaging;

import com.ecom.inventory.domain.ReservationStatus;
import com.ecom.inventory.service.InventoryService;
import com.ecom.inventory.web.dto.ReservationRequest;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ReservationEventConsumer {
    private final InventoryService inventoryService;
    private final InventoryEventProducer eventProducer;

    public ReservationEventConsumer(InventoryService inventoryService, InventoryEventProducer eventProducer) {
        this.inventoryService = inventoryService;
        this.eventProducer = eventProducer;
    }

    @KafkaListener(topics = InventoryTopics.RESERVATION_REQUESTED, groupId = "${spring.application.name}")
    public void handleReservationRequested(ReservationRequest request) {
        var result = inventoryService.reserve(request);
        if (result.status() == ReservationStatus.RESERVED) {
            eventProducer.publishReserved(result);
            return;
        }
        eventProducer.publishReservationFailed(result);
    }
}
