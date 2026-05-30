package com.ecom.order.messaging;

import com.ecom.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.FixedBackOff;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class PaymentEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);
    private final OrderService orderService;
    private final OrderEventProducer eventProducer;

    public PaymentEventConsumer(OrderService orderService, OrderEventProducer eventProducer) {
        this.orderService = orderService;
        this.eventProducer = eventProducer;
    }

    @KafkaListener(
        topics = OrderTopics.PAYMENT_SUCCEEDED,
        groupId = "${spring.application.name}",
        concurrency = "3",
        errorHandler = "kafkaErrorHandler"
    )
    public void handleSucceeded(PaymentEvent event) {
        log.info("Processing PAYMENT_SUCCEEDED for orderId={}", event.orderId());
        try {
            eventProducer.publishOrderConfirmed(orderService.markConfirmed(event.orderId()));
            log.info("Successfully processed PAYMENT_SUCCEEDED for orderId={}", event.orderId());
        } catch (Exception e) {
            log.error("Failed to process PAYMENT_SUCCEEDED for orderId={}: {}", event.orderId(), e.getMessage());
            throw e; // Re-throw for retry/DLQ handling
        }
    }

    @KafkaListener(
        topics = OrderTopics.PAYMENT_FAILED,
        groupId = "${spring.application.name}",
        concurrency = "3",
        errorHandler = "kafkaErrorHandler"
    )
    public void handleFailed(PaymentEvent event) {
        log.info("Processing PAYMENT_FAILED for orderId={}", event.orderId());
        try {
            eventProducer.publishOrderCancelled(orderService.cancelAfterPaymentFailure(event.orderId()));
            log.info("Successfully processed PAYMENT_FAILED for orderId={}", event.orderId());
        } catch (Exception e) {
            log.error("Failed to process PAYMENT_FAILED for orderId={}: {}", event.orderId(), e.getMessage());
            throw e;
        }
    }

    public record PaymentEvent(UUID paymentId, UUID orderId, UUID userId, BigDecimal amount, String currency,
                               String status) {
    }
}