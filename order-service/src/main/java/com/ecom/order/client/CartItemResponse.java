package com.ecom.order.client;

import java.math.BigDecimal;
import java.util.UUID;

public record CartItemResponse(UUID productId, String productName, BigDecimal unitPrice, int quantity, String imageUrl,
                               BigDecimal subtotal) {
}
