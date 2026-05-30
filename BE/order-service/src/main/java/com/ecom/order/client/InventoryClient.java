package com.ecom.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "inventory-service")
public interface InventoryClient {
    @PostMapping("/api/inventory/reservations")
    ReservationResult reserve(@RequestBody ReservationRequest request);

    @PostMapping("/api/inventory/reservations/{reservationId}/release")
    void release(@PathVariable("reservationId") UUID reservationId);

    record ReservationRequest(UUID orderId, List<ReservationItemRequest> items) {
    }

    record ReservationItemRequest(UUID productId, int quantity) {
    }

    record ReservationResult(UUID reservationId, UUID orderId, String status, String failureReason) {
    }
}
