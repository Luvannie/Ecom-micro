package com.ecom.inventory.messaging;

public final class InventoryTopics {
    public static final String RESERVATION_REQUESTED = "inventory.reservation-requested";
    public static final String RESERVED = "inventory.reserved";
    public static final String RESERVATION_FAILED = "inventory.reservation-failed";
    public static final String ORDER_CANCELLED = "order.cancelled";

    private InventoryTopics() {
    }
}
