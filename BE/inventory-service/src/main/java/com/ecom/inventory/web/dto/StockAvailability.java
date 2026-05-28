package com.ecom.inventory.web.dto;

import java.util.UUID;

public record StockAvailability(UUID productId, int availableQuantity, int reservedQuantity) {
}
