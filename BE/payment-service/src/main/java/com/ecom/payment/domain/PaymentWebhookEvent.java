package com.ecom.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_webhook_events")
public class PaymentWebhookEvent {
    @Id
    @Column(length = 160)
    private String providerEventId;

    @Column(nullable = false)
    private UUID paymentId;

    @Column(nullable = false, length = 80)
    private String eventType;

    @Column(nullable = false)
    private Instant processedAt;

    protected PaymentWebhookEvent() {
    }

    public PaymentWebhookEvent(String providerEventId, UUID paymentId, String eventType) {
        this.providerEventId = providerEventId;
        this.paymentId = paymentId;
        this.eventType = eventType;
    }

    @PrePersist
    void prePersist() {
        processedAt = Instant.now();
    }
}
