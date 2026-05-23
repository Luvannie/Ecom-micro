package com.ecom.notification.service;

import com.ecom.notification.domain.NotificationStatus;
import com.ecom.notification.repository.NotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
class NotificationServiceTest {
    @Autowired
    private TemplateRenderer templateRenderer;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationLogRepository repository;

    @MockBean
    private MockEmailSender emailSender;

    @BeforeEach
    void resetState() {
        repository.deleteAll();
    }

    @Test
    void templateRendererInjectsOrderIdAndTotalAmount() {
        UUID orderId = UUID.randomUUID();

        String body = templateRenderer.render("payment-succeeded", Map.of(
                "orderId", orderId,
                "total", new BigDecimal("25.00"),
                "email", "customer@example.com"));

        assertThat(body).contains(orderId.toString());
        assertThat(body).contains("25.00");
    }

    @Test
    void successfulEmailCreatesSentNotificationLog() {
        var log = notificationService.sendEmail(UUID.randomUUID(), "customer@example.com", "payment-succeeded",
                Map.of("orderId", UUID.randomUUID(), "total", new BigDecimal("25.00")));

        assertThat(log.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(repository.findAll()).singleElement()
                .extracting("status")
                .isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void senderFailureCreatesFailedNotificationLog() {
        doThrow(new RuntimeException("smtp unavailable")).when(emailSender)
                .send(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString());

        var log = notificationService.sendEmail(UUID.randomUUID(), "customer@example.com", "payment-failed",
                Map.of("orderId", UUID.randomUUID(), "total", new BigDecimal("25.00")));

        assertThat(log.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(log.getFailureReason()).contains("smtp unavailable");
    }
}
