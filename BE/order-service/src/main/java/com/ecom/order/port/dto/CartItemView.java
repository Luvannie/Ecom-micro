package com.ecom.order.port.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Framework-agnostic view of a single cart item. */
public record CartItemView(UUID productId, String productName,
                           BigDecimal unitPrice, int quantity) {
}
