package com.ecom.order.messaging;

import com.ecom.order.service.OrderService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class PaymentEventConsumer {
    private final OrderService orderService;
    private final OrderEventProducer eventProducer;

    public PaymentEventConsumer(OrderService orderService, OrderEventProducer eventProducer) {
        this.orderService = orderService;
        this.eventProducer = eventProducer;
    }

    @KafkaListener(topics = OrderTopics.PAYMENT_SUCCEEDED, groupId = "${spring.application.name}")
    public void handleSucceeded(PaymentEvent event) {
        eventProducer.publishOrderConfirmed(orderService.markConfirmed(event.orderId()));
    }

    @KafkaListener(topics = OrderTopics.PAYMENT_FAILED, groupId = "${spring.application.name}")
    public void handleFailed(PaymentEvent event) {
        eventProducer.publishOrderCancelled(orderService.cancelAfterPaymentFailure(event.orderId()));
    }

    public record PaymentEvent(UUID paymentId, UUID orderId, UUID userId, BigDecimal amount, String currency,
                               String status) {
    }
}
