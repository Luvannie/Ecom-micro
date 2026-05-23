package com.ecom.inventory.web.dto;

import com.ecom.inventory.domain.ReservationStatus;

import java.util.UUID;

public record ReservationResult(UUID reservationId, UUID orderId, ReservationStatus status, String failureReason) {
}
