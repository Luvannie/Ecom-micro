package com.ecom.inventory.messaging;

import com.ecom.inventory.web.dto.ReservationResult;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class InventoryEventProducer {
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    public InventoryEventProducer(KafkaTemplate<Object, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishReserved(ReservationResult result) {
        try {
            kafkaTemplate.send(InventoryTopics.RESERVED, result.orderId().toString(), result)
                    .get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish RESERVED event for order " + result.orderId(), e);
        }
    }

    public void publishReservationFailed(ReservationResult result) {
        try {
            kafkaTemplate.send(InventoryTopics.RESERVATION_FAILED, result.orderId().toString(), result)
                    .get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish RESERVATION_FAILED event for order " + result.orderId(), e);
        }
    }
}