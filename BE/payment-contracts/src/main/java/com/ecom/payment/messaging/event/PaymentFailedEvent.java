package com.ecom.payment.messaging.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentFailedEvent(
        UUID eventId,
        Instant occurredAt,
        int eventVersion,
        UUID paymentId,
        UUID orderId,
        UUID userId,
        BigDecimal amount,
        String currency,
        String reason
) implements PaymentEvent {
    public static PaymentFailedEvent of(UUID paymentId, UUID orderId, UUID userId,
                                        BigDecimal amount, String currency, String reason) {
        return new PaymentFailedEvent(UUID.randomUUID(), Instant.now(), 1,
                paymentId, orderId, userId, amount, currency, reason);
    }
}
