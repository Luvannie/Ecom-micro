package com.ecom.payment.messaging.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentRefundedEvent(
        UUID eventId,
        Instant occurredAt,
        int eventVersion,
        UUID paymentId,
        UUID orderId,
        UUID userId,
        BigDecimal amount,
        String reason
) implements PaymentEvent {
    public static PaymentRefundedEvent of(UUID paymentId, UUID orderId, UUID userId,
                                          BigDecimal amount, String reason) {
        return new PaymentRefundedEvent(UUID.randomUUID(), Instant.now(), 1,
                paymentId, orderId, userId, amount, reason);
    }
}
