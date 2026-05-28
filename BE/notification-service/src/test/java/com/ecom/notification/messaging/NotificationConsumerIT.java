package com.ecom.notification.messaging;

import com.ecom.notification.repository.NotificationLogRepository;
import com.ecom.notification.service.MockEmailSender;
import com.ecom.notification.service.MockSmsSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class NotificationConsumerIT {
    @Autowired
    private PaymentEventConsumer paymentEventConsumer;

    @Autowired
    private OrderEventConsumer orderEventConsumer;

    @Autowired
    private NotificationLogRepository repository;

    @MockBean
    private MockEmailSender emailSender;

    @MockBean
    private MockSmsSender smsSender;

    @BeforeEach
    void resetState() {
        repository.deleteAll();
    }

    @Test
    void paymentSuccessEventCreatesNotificationLog() {
        paymentEventConsumer.handleSucceeded(new PaymentEventConsumer.PaymentEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("25.00"), "USD", "SUCCEEDED"));

        assertThat(repository.findAll()).singleElement()
                .satisfies(log -> assertThat(log.getTemplateName()).isEqualTo("payment-succeeded"));
    }

    @Test
    void orderCancelledEventCreatesEmailAndSmsLogs() {
        orderEventConsumer.handleCancelled(new OrderEventConsumer.OrderCancelledEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));

        assertThat(repository.findAll()).hasSize(2)
                .extracting("templateName")
                .contains("order-cancelled", "sms");
    }
}
