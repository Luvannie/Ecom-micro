package com.ecom.notification.messaging;

import com.ecom.common.messaging.KafkaListenerResilienceWrapper;
import com.ecom.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Component
public class PaymentEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);
    private final NotificationService notificationService;
    private final KafkaListenerResilienceWrapper wrapper;

    public PaymentEventConsumer(NotificationService notificationService,
                                KafkaListenerResilienceWrapper wrapper) {
        this.notificationService = notificationService;
        this.wrapper = wrapper;
    }

    @KafkaListener(
        topics = "payment.succeeded",
        groupId = "${spring.application.name}",
        concurrency = "2"
    )
    public void handleSucceeded(PaymentEvent event) {
        log.info("Processing PAYMENT_SUCCEEDED for paymentId={}, orderId={}",
                event.paymentId(), event.orderId());
        wrapper.execute(
            () -> {
                notificationService.sendEmail(event.userId(), recipient(event.userId()), "payment-succeeded",
                        model(event));
                log.info("Successfully sent PAYMENT_SUCCEEDED email for paymentId={}", event.paymentId());
                return null;
            },
            ex -> log.warn("Timeout/breaker open for notification.payment-succeeded (paymentId={}): {}",
                           event.paymentId(), ex.getMessage())
        );
    }

    @KafkaListener(
        topics = "payment.failed",
        groupId = "${spring.application.name}",
        concurrency = "2"
    )
    public void handleFailed(PaymentEvent event) {
        log.info("Processing PAYMENT_FAILED for paymentId={}, orderId={}",
                event.paymentId(), event.orderId());
        wrapper.execute(
            () -> {
                notificationService.sendEmail(event.userId(), recipient(event.userId()), "payment-failed",
                        model(event));
                log.info("Successfully sent PAYMENT_FAILED email for paymentId={}", event.paymentId());
                return null;
            },
            ex -> log.warn("Timeout/breaker open for notification.payment-failed (paymentId={}): {}",
                           event.paymentId(), ex.getMessage())
        );
    }

    private Map<String, Object> model(PaymentEvent event) {
        return Map.of("paymentId", event.paymentId(), "orderId", event.orderId(), "total", event.amount(),
                "currency", event.currency(), "email", recipient(event.userId()));
    }

    // TODO: Replace with actual user email lookup from user-service
    private String recipient(UUID userId) {
        return "user-" + userId + "@example.test";
    }

    public record PaymentEvent(UUID paymentId, UUID orderId, UUID userId, BigDecimal amount, String currency,
                               String status) {
    }
}
