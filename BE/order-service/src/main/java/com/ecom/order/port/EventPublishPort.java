package com.ecom.order.port;

/** Port for publishing order-lifecycle events. The adapter implementation
 *  wraps the existing Kafka producer; the port keeps the interface clean
 *  so {@code OrderService} does not depend on Spring Kafka. */
public interface EventPublishPort {
    void publishReservationRequested(Object event);
    void publishOrderConfirmed(Object event);
    void publishOrderCancelled(Object event);
}
