package com.ecom.cart.web.dto;

import com.ecom.cart.domain.Cart;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CartResponse(UUID userId, List<CartItemResponse> items, BigDecimal total, Instant updatedAt) {
    public static CartResponse from(Cart cart) {
        return new CartResponse(
                cart.getUserId(),
                cart.getItems().stream()
                        .map(item -> new CartItemResponse(item.getProductId(), item.getProductName(), item.getUnitPrice(),
                                item.getQuantity(), item.getImageUrl(), item.subtotal()))
                        .toList(),
                cart.total(),
                cart.getUpdatedAt());
    }
}
