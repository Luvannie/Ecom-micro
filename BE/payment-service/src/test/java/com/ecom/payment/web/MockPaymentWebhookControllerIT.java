package com.ecom.payment.web;

import com.ecom.payment.repository.OutboxEventRepository;
import com.ecom.payment.repository.PaymentRepository;
import com.ecom.payment.repository.PaymentWebhookEventRepository;
import com.ecom.payment.repository.RefundRepository;
import com.ecom.payment.service.PaymentService;
import com.ecom.payment.web.dto.CreatePaymentRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MockPaymentWebhookControllerIT {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private PaymentWebhookEventRepository webhookEventRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @BeforeEach
    void resetState() {
        webhookEventRepository.deleteAll();
        refundRepository.deleteAll();
        outboxEventRepository.deleteAll();
        paymentRepository.deleteAll();
    }

    @Test
    void duplicateWebhookEventIsIdempotent() throws Exception {
        var payment = createPayment();

        postWebhook("evt-1", payment.providerPaymentId(), "PAYMENT_SUCCEEDED", null);
        postWebhook("evt-1", payment.providerPaymentId(), "PAYMENT_SUCCEEDED", null);

        assertThat(webhookEventRepository.findAll()).hasSize(1);
        assertThat(outboxEventRepository.findAll()).hasSize(1);
    }

    @Test
    void successWebhookEmitsPaymentSucceeded() throws Exception {
        var payment = createPayment();

        postWebhook("evt-success", payment.providerPaymentId(), "PAYMENT_SUCCEEDED", null);

        assertThat(outboxEventRepository.findAll()).singleElement()
                .satisfies(event -> assertThat(event.getEventType()).isEqualTo("payment.succeeded"));
    }

    @Test
    void failureWebhookEmitsPaymentFailed() throws Exception {
        var payment = createPayment();

        postWebhook("evt-failed", payment.providerPaymentId(), "PAYMENT_FAILED", "mock failure");

        assertThat(outboxEventRepository.findAll()).singleElement()
                .satisfies(event -> assertThat(event.getEventType()).isEqualTo("payment.failed"));
    }

    private com.ecom.payment.web.dto.PaymentResponse createPayment() {
        return paymentService.createPayment(UUID.randomUUID(),
                new CreatePaymentRequest(UUID.randomUUID(), new BigDecimal("25.00"), "USD"), UUID.randomUUID().toString());
    }

    private void postWebhook(String providerEventId, String providerPaymentId, String eventType, String reason) throws Exception {
        mockMvc.perform(post("/api/payments/webhooks/mock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "providerEventId", providerEventId,
                                "providerPaymentId", providerPaymentId,
                                "eventType", eventType,
                                "reason", reason == null ? "" : reason))))
                .andExpect(status().isOk());
    }
}
