package com.ecom.payment.messaging.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentEventSerializationTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void succeeded_event_round_trips() throws Exception {
        PaymentSucceededEvent ev = PaymentSucceededEvent.of(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("10.00"), "USD");
        String json = mapper.writeValueAsString(ev);
        PaymentSucceededEvent back = mapper.readValue(json, PaymentSucceededEvent.class);
        assertThat(back.eventId()).isEqualTo(ev.eventId());
        assertThat(back.eventVersion()).isEqualTo(1);
        assertThat(back.amount()).isEqualByComparingTo("10.00");
    }

    @Test
    void failed_event_round_trips_with_reason() throws Exception {
        PaymentFailedEvent ev = PaymentFailedEvent.of(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("5.00"), "USD", "card declined");
        String json = mapper.writeValueAsString(ev);
        PaymentFailedEvent back = mapper.readValue(json, PaymentFailedEvent.class);
        assertThat(back.reason()).isEqualTo("card declined");
    }

    @Test
    void refunded_event_round_trips() throws Exception {
        PaymentRefundedEvent ev = PaymentRefundedEvent.of(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("3.00"), "customer requested");
        String json = mapper.writeValueAsString(ev);
        PaymentRefundedEvent back = mapper.readValue(json, PaymentRefundedEvent.class);
        assertThat(back.amount()).isEqualByComparingTo("3.00");
        assertThat(back.reason()).isEqualTo("customer requested");
    }
}
