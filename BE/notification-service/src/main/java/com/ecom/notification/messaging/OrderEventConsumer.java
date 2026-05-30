package com.ecom.notification.messaging;

import com.ecom.notification.service.NotificationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Component
public class OrderEventConsumer {
    private final NotificationService notificationService;

    public OrderEventConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = "order.confirmed", groupId = "${spring.application.name}")
    public void handleConfirmed(OrderConfirmedEvent event) {
        notificationService.sendEmail(event.userId(), recipient(event.userId()), "order-confirmed",
                Map.of("orderId", event.orderId(), "total", event.total(), "email", recipient(event.userId())));
    }

    @KafkaListener(topics = "order.cancelled", groupId = "${spring.application.name}")
    public void handleCancelled(OrderCancelledEvent event) {
        notificationService.sendEmail(event.userId(), recipient(event.userId()), "order-cancelled",
                Map.of("orderId", event.orderId(), "total", BigDecimal.ZERO, "email", recipient(event.userId())));
        notificationService.sendSms(event.userId(), "+10000000000", "Order cancelled: " + event.orderId());
    }

    private String recipient(UUID userId) {
        return "user-" + userId + "@example.test";
    }

    public record OrderConfirmedEvent(UUID orderId, UUID userId, BigDecimal total) {
    }

    public record OrderCancelledEvent(UUID orderId, UUID userId, UUID reservationId) {
    }
}
