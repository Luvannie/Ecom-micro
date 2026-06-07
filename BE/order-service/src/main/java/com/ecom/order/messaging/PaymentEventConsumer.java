package com.ecom.order.messaging;

import com.ecom.common.messaging.KafkaListenerResilienceWrapper;
import com.ecom.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class PaymentEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);
    private final OrderService orderService;
    private final OrderEventProducer eventProducer;
    private final KafkaListenerResilienceWrapper wrapper;

    public PaymentEventConsumer(OrderService orderService,
                                OrderEventProducer eventProducer,
                                KafkaListenerResilienceWrapper wrapper) {
        this.orderService = orderService;
        this.eventProducer = eventProducer;
        this.wrapper = wrapper;
    }

    @KafkaListener(
        topics = OrderTopics.PAYMENT_SUCCEEDED,
        groupId = "${spring.application.name}",
        concurrency = "3"
    )
    public void handleSucceeded(PaymentEvent event) {
        log.info("Processing PAYMENT_SUCCEEDED for orderId={}", event.orderId());
        wrapper.execute(
            () -> {
                eventProducer.publishOrderConfirmed(orderService.markConfirmed(event.orderId()));
                log.info("Successfully processed PAYMENT_SUCCEEDED for orderId={}", event.orderId());
                return null;
            },
            ex -> log.warn("Timeout/breaker open for order.payment-succeeded (orderId={}): {}",
                           event.orderId(), ex.getMessage())
        );
    }

    @KafkaListener(
        topics = OrderTopics.PAYMENT_FAILED,
        groupId = "${spring.application.name}",
        concurrency = "3"
    )
    public void handleFailed(PaymentEvent event) {
        log.info("Processing PAYMENT_FAILED for orderId={}", event.orderId());
        wrapper.execute(
            () -> {
                eventProducer.publishOrderCancelled(orderService.cancelAfterPaymentFailure(event.orderId()));
                log.info("Successfully processed PAYMENT_FAILED for orderId={}", event.orderId());
                return null;
            },
            ex -> log.warn("Timeout/breaker open for order.payment-failed (orderId={}): {}",
                           event.orderId(), ex.getMessage())
        );
    }

    public record PaymentEvent(UUID paymentId, UUID orderId, UUID userId, BigDecimal amount, String currency,
                               String status) {
    }
}
