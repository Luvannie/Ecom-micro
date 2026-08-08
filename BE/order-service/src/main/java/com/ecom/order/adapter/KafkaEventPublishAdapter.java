package com.ecom.order.adapter;

import com.ecom.order.messaging.OrderEventProducer;
import com.ecom.order.port.EventPublishPort;
import org.springframework.stereotype.Component;

@Component
class KafkaEventPublishAdapter implements EventPublishPort {

    private final OrderEventProducer producer;

    KafkaEventPublishAdapter(OrderEventProducer producer) {
        this.producer = producer;
    }

    @Override
    public void publishReservationRequested(Object event) {
        producer.publishReservationRequested((com.ecom.order.web.dto.OrderResponse) event);
    }

    @Override
    public void publishOrderConfirmed(Object event) {
        producer.publishOrderConfirmed((com.ecom.order.web.dto.OrderResponse) event);
    }

    @Override
    public void publishOrderCancelled(Object event) {
        producer.publishOrderCancelled((com.ecom.order.web.dto.OrderResponse) event);
    }
}
