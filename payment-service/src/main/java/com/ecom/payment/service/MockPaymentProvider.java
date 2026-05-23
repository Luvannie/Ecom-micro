package com.ecom.payment.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class MockPaymentProvider {
    public ProviderPayment createPayment(UUID paymentId, BigDecimal amount, String currency) {
        String providerPaymentId = "mock-pay-" + paymentId;
        return new ProviderPayment(providerPaymentId, "PENDING", "https://payments.example.test/" + providerPaymentId);
    }

    public ProviderPayment markSucceeded(String providerPaymentId) {
        return new ProviderPayment(providerPaymentId, "SUCCEEDED", null);
    }

    public ProviderPayment markFailed(String providerPaymentId, String reason) {
        return new ProviderPayment(providerPaymentId, "FAILED", null);
    }

    public record ProviderPayment(String providerPaymentId, String status, String redirectUrl) {
    }
}
