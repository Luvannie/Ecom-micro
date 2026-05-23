package com.ecom.order.web.dto;

import com.ecom.order.domain.Order;
import com.ecom.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderSummaryResponse(UUID id, OrderStatus status, BigDecimal total, Instant createdAt) {
    public static OrderSummaryResponse from(Order order) {
        return new OrderSummaryResponse(order.getId(), order.getStatus(), order.getTotal(), order.getCreatedAt());
    }
}
