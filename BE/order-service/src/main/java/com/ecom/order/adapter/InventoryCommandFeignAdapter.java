package com.ecom.order.adapter;

import com.ecom.order.client.InventoryClient;
import com.ecom.order.port.InventoryCommandPort;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class InventoryCommandFeignAdapter implements InventoryCommandPort {

    private final InventoryClient client;

    InventoryCommandFeignAdapter(InventoryClient client) {
        this.client = client;
    }

    @Override
    public void release(UUID reservationId) {
        client.release(reservationId);
    }
}
