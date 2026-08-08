package com.ecom.payment.messaging.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentSucceededEvent(
        UUID eventId,
        Instant occurredAt,
        int eventVersion,
        UUID paymentId,
        UUID orderId,
        UUID userId,
        BigDecimal amount,
        String currency
) implements PaymentEvent {
    public static PaymentSucceededEvent of(UUID paymentId, UUID orderId, UUID userId,
                                           BigDecimal amount, String currency) {
        return new PaymentSucceededEvent(UUID.randomUUID(), Instant.now(), 1,
                paymentId, orderId, userId, amount, currency);
    }
}
