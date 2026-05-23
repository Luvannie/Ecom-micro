package com.ecom.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_audit_logs")
public class StockAuditLog {
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID productId;

    @Column(nullable = false, length = 60)
    private String changeType;

    @Column(nullable = false)
    private int quantityDelta;

    @Column(nullable = false, length = 255)
    private String reason;

    @Column(nullable = false)
    private Instant createdAt;

    protected StockAuditLog() {
    }

    public StockAuditLog(UUID productId, String changeType, int quantityDelta, String reason) {
        this.id = UUID.randomUUID();
        this.productId = productId;
        this.changeType = changeType;
        this.quantityDelta = quantityDelta;
        this.reason = reason;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public String getChangeType() {
        return changeType;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProductId() {
        return productId;
    }

    public int getQuantityDelta() {
        return quantityDelta;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
