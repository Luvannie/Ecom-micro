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
public class OrderEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);
    private final NotificationService notificationService;
    private final KafkaListenerResilienceWrapper wrapper;

    public OrderEventConsumer(NotificationService notificationService,
                              KafkaListenerResilienceWrapper wrapper) {
        this.notificationService = notificationService;
        this.wrapper = wrapper;
    }

    @KafkaListener(
        topics = "order.confirmed",
        groupId = "${spring.application.name}",
        concurrency = "2"
    )
    public void handleConfirmed(OrderConfirmedEvent event) {
        log.info("Processing ORDER_CONFIRMED for orderId={}, userId={}", event.orderId(), event.userId());
        wrapper.execute(
            () -> {
                notificationService.sendEmail(event.userId(), recipient(event.userId()), "order-confirmed",
                        Map.of("orderId", event.orderId(), "total", event.total(), "email", recipient(event.userId())));
                log.info("Successfully sent ORDER_CONFIRMED email for orderId={}", event.orderId());
                return null;
            },
            ex -> log.warn("Timeout/breaker open for notification.order-confirmed (orderId={}): {}",
                           event.orderId(), ex.getMessage())
        );
    }

    @KafkaListener(
        topics = "order.cancelled",
        groupId = "${spring.application.name}",
        concurrency = "2"
    )
    public void handleCancelled(OrderCancelledEvent event) {
        log.info("Processing ORDER_CANCELLED for orderId={}, userId={}", event.orderId(), event.userId());
        wrapper.execute(
            () -> {
                notificationService.sendEmail(event.userId(), recipient(event.userId()), "order-cancelled",
                        Map.of("orderId", event.orderId(), "total", BigDecimal.ZERO, "email", recipient(event.userId())));
                notificationService.sendSms(event.userId(), "+10000000000", "Order cancelled: " + event.orderId());
                log.info("Successfully sent ORDER_CANCELLED notifications for orderId={}", event.orderId());
                return null;
            },
            ex -> log.warn("Timeout/breaker open for notification.order-cancelled (orderId={}): {}",
                           event.orderId(), ex.getMessage())
        );
    }

    // TODO: Replace with actual user phone lookup from user-service
    private String recipient(UUID userId) {
        return "user-" + userId + "@example.test";
    }

    public record OrderConfirmedEvent(UUID orderId, UUID userId, BigDecimal total) {
    }

    public record OrderCancelledEvent(UUID orderId, UUID userId, UUID reservationId) {
    }
}
