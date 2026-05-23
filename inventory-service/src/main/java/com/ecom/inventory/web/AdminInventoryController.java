package com.ecom.inventory.web;

import com.ecom.inventory.service.InventoryService;
import com.ecom.inventory.web.dto.StockAuditLogResponse;
import com.ecom.inventory.web.dto.SetStockRequest;
import com.ecom.inventory.web.dto.StockAvailability;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class AdminInventoryController {
    private final InventoryService inventoryService;

    public AdminInventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PutMapping("/api/admin/inventory/products/{productId}")
    public StockAvailability setStock(@PathVariable("productId") UUID productId,
                                      @Valid @RequestBody SetStockRequest request) {
        return inventoryService.setStock(productId, request);
    }

    @GetMapping("/api/admin/inventory/audit")
    public List<StockAuditLogResponse> audit(@RequestParam("productId") UUID productId) {
        return inventoryService.audit(productId).stream()
                .map(StockAuditLogResponse::from)
                .toList();
    }
}
