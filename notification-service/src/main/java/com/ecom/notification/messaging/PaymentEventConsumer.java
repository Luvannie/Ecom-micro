package com.ecom.notification.messaging;

import com.ecom.notification.service.NotificationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Component
public class PaymentEventConsumer {
    private final NotificationService notificationService;

    public PaymentEventConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = "payment.succeeded", groupId = "${spring.application.name}")
    public void handleSucceeded(PaymentEvent event) {
        notificationService.sendEmail(event.userId(), recipient(event.userId()), "payment-succeeded", model(event));
    }

    @KafkaListener(topics = "payment.failed", groupId = "${spring.application.name}")
    public void handleFailed(PaymentEvent event) {
        notificationService.sendEmail(event.userId(), recipient(event.userId()), "payment-failed", model(event));
    }

    private Map<String, Object> model(PaymentEvent event) {
        return Map.of("paymentId", event.paymentId(), "orderId", event.orderId(), "total", event.amount(),
                "currency", event.currency(), "email", recipient(event.userId()));
    }

    private String recipient(UUID userId) {
        return "user-" + userId + "@example.test";
    }

    public record PaymentEvent(UUID paymentId, UUID orderId, UUID userId, BigDecimal amount, String currency,
                               String status) {
    }
}
