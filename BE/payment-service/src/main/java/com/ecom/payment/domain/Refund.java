package com.ecom.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refunds")
public class Refund {
    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private RefundStatus status;

    @Column(nullable = false, length = 255)
    private String reason;

    @Column(nullable = false)
    private Instant createdAt;

    protected Refund() {
    }

    public Refund(Payment payment, BigDecimal amount, String reason) {
        this.id = UUID.randomUUID();
        this.payment = payment;
        this.amount = amount;
        this.reason = reason;
        this.status = RefundStatus.SUCCEEDED;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }
}
