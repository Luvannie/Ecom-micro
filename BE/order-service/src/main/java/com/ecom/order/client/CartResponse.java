package com.ecom.order.client;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CartResponse(UUID userId, List<CartItemResponse> items, BigDecimal total, Instant updatedAt) {
}
