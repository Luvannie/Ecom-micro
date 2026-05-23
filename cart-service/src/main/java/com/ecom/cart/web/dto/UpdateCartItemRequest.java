package com.ecom.cart.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateCartItemRequest(@Min(0) @Max(99) int quantity) {
}
