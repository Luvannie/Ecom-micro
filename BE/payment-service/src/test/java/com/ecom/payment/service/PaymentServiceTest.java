package com.ecom.payment.service;

import com.ecom.payment.domain.PaymentStatus;
import com.ecom.payment.domain.OutboxStatus;
import com.ecom.payment.repository.OutboxEventRepository;
import com.ecom.payment.repository.PaymentRepository;
import com.ecom.payment.web.dto.CreatePaymentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PaymentServiceTest {
    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @BeforeEach
    void resetState() {
        outboxEventRepository.deleteAll();
        paymentRepository.deleteAll();
    }

    @Test
    void createPaymentIsIdempotentByIdempotencyKey() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        var first = paymentService.createPayment(userId,
                new CreatePaymentRequest(orderId, new BigDecimal("25.00"), "USD"), "pay-" + orderId);
        var second = paymentService.createPayment(userId,
                new CreatePaymentRequest(orderId, new BigDecimal("25.00"), "USD"), "pay-" + orderId);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(paymentRepository.findAll()).hasSize(1);
    }

    @Test
    void statusChangeAppendsOutboxEvent() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        var payment = paymentService.createPayment(userId,
                new CreatePaymentRequest(orderId, new BigDecimal("25.00"), "USD"), "pay-" + orderId);

        paymentService.markSucceeded(payment.providerPaymentId(), "evt-success");

        assertThat(paymentRepository.findById(payment.id())).get()
                .extracting("status")
                .isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(outboxEventRepository.findAll()).singleElement().satisfies(event -> {
            assertThat(event.getAggregateId()).isEqualTo(payment.id());
            assertThat(event.getEventType()).isEqualTo("payment.succeeded");
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(event.getPayload()).contains(orderId.toString());
        });
    }
}
