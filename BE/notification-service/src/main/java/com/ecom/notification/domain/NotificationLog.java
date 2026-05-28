package com.ecom.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_logs")
public class NotificationLog {
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationChannel channel;

    @Column(nullable = false, length = 120)
    private String templateName;

    @Column(nullable = false, length = 255)
    private String recipient;

    @Column(length = 255)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationStatus status;

    @Column(length = 500)
    private String failureReason;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant sentAt;

    protected NotificationLog() {
    }

    public NotificationLog(UUID userId, NotificationChannel channel, String templateName, String recipient, String subject) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.channel = channel;
        this.templateName = templateName;
        this.recipient = recipient;
        this.subject = subject;
        this.status = NotificationStatus.PENDING;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public void markSent() {
        status = NotificationStatus.SENT;
        sentAt = Instant.now();
    }

    public void markFailed(String failureReason) {
        status = NotificationStatus.FAILED;
        this.failureReason = failureReason;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getTemplateName() {
        return templateName;
    }
}
