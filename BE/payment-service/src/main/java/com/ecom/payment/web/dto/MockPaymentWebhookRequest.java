package com.ecom.payment.web.dto;

import jakarta.validation.constraints.NotBlank;

public record MockPaymentWebhookRequest(@NotBlank String providerEventId, @NotBlank String providerPaymentId,
                                        @NotBlank String eventType, String reason) {
}
