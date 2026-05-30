package com.ecom.payment.web.dto;

import com.ecom.payment.domain.Payment;
import com.ecom.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(UUID id, UUID orderId, UUID userId, BigDecimal amount, String currency,
                              PaymentStatus status, String providerPaymentId, String redirectUrl, Instant createdAt,
                              Instant updatedAt) {
    public static PaymentResponse from(Payment payment, String redirectUrl) {
        return new PaymentResponse(payment.getId(), payment.getOrderId(), payment.getUserId(), payment.getAmount(),
                payment.getCurrency(), payment.getStatus(), payment.getProviderPaymentId(), redirectUrl,
                payment.getCreatedAt(), payment.getUpdatedAt());
    }
}
