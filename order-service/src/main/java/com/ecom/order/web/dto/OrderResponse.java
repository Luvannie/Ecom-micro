package com.ecom.order.web.dto;

import com.ecom.order.domain.Order;
import com.ecom.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(UUID id, UUID userId, OrderStatus status, BigDecimal subtotal, BigDecimal shippingFee,
                            BigDecimal total, UUID reservationId, List<OrderItemResponse> items, Instant createdAt,
                            Instant updatedAt) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(order.getId(), order.getUserId(), order.getStatus(), order.getSubtotal(),
                order.getShippingFee(), order.getTotal(), order.getReservationId(),
                order.getItems().stream().map(OrderItemResponse::from).toList(),
                order.getCreatedAt(), order.getUpdatedAt());
    }
}
