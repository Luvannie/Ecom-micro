package com.ecom.order.port.dto;

import java.util.List;
import java.util.UUID;

/** Framework-agnostic view of a user's cart. */
public record CartView(UUID userId, List<CartItemView> items) {
}
