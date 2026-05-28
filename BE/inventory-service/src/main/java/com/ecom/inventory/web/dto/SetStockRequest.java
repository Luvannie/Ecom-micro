package com.ecom.inventory.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record SetStockRequest(@Min(0) int quantity, @NotBlank String reason) {
}
