package com.ecom.inventory.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReservationItemRequest(@NotNull UUID productId, @Min(1) int quantity) {
}
