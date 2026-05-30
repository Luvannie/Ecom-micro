package com.ecom.inventory.web;

import com.ecom.inventory.service.InventoryService;
import com.ecom.inventory.web.dto.ReservationRequest;
import com.ecom.inventory.web.dto.ReservationResult;
import com.ecom.inventory.web.dto.StockAvailability;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
public class InventoryController {
    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/api/inventory/products/{productId}")
    public StockAvailability availability(@PathVariable("productId") UUID productId) {
        return inventoryService.getAvailability(productId);
    }

    @PostMapping("/api/inventory/reservations")
    public ResponseEntity<ReservationResult> reserve(@Valid @RequestBody ReservationRequest request) {
        ReservationResult result = inventoryService.reserve(request);
        return ResponseEntity.created(URI.create("/api/inventory/reservations/" + result.reservationId())).body(result);
    }

    @PostMapping("/api/inventory/reservations/{reservationId}/release")
    public ResponseEntity<Void> release(@PathVariable("reservationId") UUID reservationId) {
        inventoryService.release(reservationId, "api release");
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/inventory/reservations/{reservationId}/commit")
    public ResponseEntity<Void> commit(@PathVariable("reservationId") UUID reservationId) {
        inventoryService.commit(reservationId, "api commit");
        return ResponseEntity.noContent().build();
    }
}