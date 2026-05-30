package com.ecom.payment.web;

import com.ecom.payment.repository.OutboxEventRepository;
import com.ecom.payment.repository.PaymentRepository;
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

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentControllerIT {
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
    private OutboxEventRepository outboxEventRepository;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void resetState() {
        refundRepository.deleteAll();
        outboxEventRepository.deleteAll();
        paymentRepository.deleteAll();
    }

    @Test
    void missingIdempotencyKeyReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .header("X-User-Id", userId)
                        .header("X-User-Email", "customer@example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateCreatePaymentWithSameKeyReturnsSamePaymentId() throws Exception {
        UUID orderId = UUID.randomUUID();

        String first = mockMvc.perform(post("/api/payments")
                        .header("X-User-Id", userId)
                        .header("X-User-Email", "customer@example.com")
                        .header("Idempotency-Key", "pay-" + orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody(orderId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andReturn().getResponse().getContentAsString();

        String paymentId = objectMapper.readTree(first).get("id").asText();

        mockMvc.perform(post("/api/payments")
                        .header("X-User-Id", userId)
                        .header("X-User-Email", "customer@example.com")
                        .header("Idempotency-Key", "pay-" + orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody(orderId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(paymentId));
    }

    @Test
    void userCannotReadAnotherUsersPayment() throws Exception {
        var payment = paymentService.createPayment(UUID.randomUUID(),
                new CreatePaymentRequest(UUID.randomUUID(), new BigDecimal("25.00"), "USD"), "pay-other");

        mockMvc.perform(get("/api/payments/{paymentId}", payment.id())
                        .header("X-User-Id", userId)
                        .header("X-User-Email", "customer@example.com"))
                .andExpect(status().isNotFound());
    }

    @Test
    void refundWithoutAdminRoleReturnsForbidden() throws Exception {
        var payment = succeededPayment();

        mockMvc.perform(post("/api/payments/{paymentId}/refund", payment.id())
                        .header("X-User-Id", userId)
                        .header("X-User-Email", "customer@example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("amount", "25.00", "reason", "customer request"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void refundSucceededPaymentEmitsOutboxEvent() throws Exception {
        var payment = succeededPayment();

        mockMvc.perform(post("/api/payments/{paymentId}/refund", payment.id())
                        .header("X-User-Id", userId)
                        .header("X-User-Email", "admin@example.com")
                        .header("X-User-Roles", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("amount", "25.00", "reason", "customer request"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));

        org.assertj.core.api.Assertions.assertThat(outboxEventRepository.findAll())
                .anySatisfy(event -> org.assertj.core.api.Assertions.assertThat(event.getEventType())
                        .isEqualTo("payment.refunded"));
    }

    private com.ecom.payment.web.dto.PaymentResponse succeededPayment() {
        var payment = paymentService.createPayment(userId,
                new CreatePaymentRequest(UUID.randomUUID(), new BigDecimal("25.00"), "USD"), "pay-success");
        paymentService.markSucceeded(payment.providerPaymentId(), "evt-success");
        outboxEventRepository.deleteAll();
        return paymentService.getPayment(userId, payment.id());
    }

    private String paymentBody(UUID orderId) throws Exception {
        return objectMapper.writeValueAsString(new CreatePaymentRequest(orderId, new BigDecimal("25.00"), "USD"));
    }
}
