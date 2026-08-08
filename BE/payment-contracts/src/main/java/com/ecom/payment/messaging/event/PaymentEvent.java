package com.ecom.payment.messaging.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Common envelope for all payment-domain events published via the
 * transactional outbox.
 *
 * <p>All events carry the same header fields so consumers can route
 * and deduplicate without inspecting the body. Per-type payload fields
 * are added by the concrete {@code permits} classes.
 */
public sealed interface PaymentEvent
        permits PaymentSucceededEvent, PaymentFailedEvent, PaymentRefundedEvent {

    UUID eventId();
    Instant occurredAt();
    int eventVersion();
    UUID paymentId();
    UUID orderId();
    UUID userId();
}
