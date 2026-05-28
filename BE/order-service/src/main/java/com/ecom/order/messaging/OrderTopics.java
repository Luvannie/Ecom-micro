package com.ecom.order.messaging;

public final class OrderTopics {
    public static final String INVENTORY_RESERVATION_REQUESTED = "inventory.reservation-requested";
    public static final String INVENTORY_RESERVED = "inventory.reserved";
    public static final String INVENTORY_RESERVATION_FAILED = "inventory.reservation-failed";
    public static final String ORDER_CANCELLED = "order.cancelled";
    public static final String ORDER_CONFIRMED = "order.confirmed";
    public static final String PAYMENT_SUCCEEDED = "payment.succeeded";
    public static final String PAYMENT_FAILED = "payment.failed";

    private OrderTopics() {
    }
}
