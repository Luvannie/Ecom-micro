package com.ecom.order.port;

import java.util.UUID;

/** Port for sending commands to the inventory service (e.g. release a reservation). */
public interface InventoryCommandPort {
    void release(UUID reservationId);
}
