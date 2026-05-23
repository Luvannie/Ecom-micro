package com.ecom.inventory.messaging;

import com.ecom.inventory.web.dto.ReservationResult;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class InventoryEventProducer {
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    public InventoryEventProducer(KafkaTemplate<Object, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishReserved(ReservationResult result) {
        kafkaTemplate.send(InventoryTopics.RESERVED, result.orderId().toString(), result);
    }

    public void publishReservationFailed(ReservationResult result) {
        kafkaTemplate.send(InventoryTopics.RESERVATION_FAILED, result.orderId().toString(), result);
    }
}
