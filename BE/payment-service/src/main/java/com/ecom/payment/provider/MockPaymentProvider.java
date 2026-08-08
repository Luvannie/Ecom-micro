package com.ecom.payment.provider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Default in-memory {@link PaymentProvider} adapter. Used in dev and
 * test environments; in production a real provider (Stripe, VNPay, ...)
 * is wired in by enabling its own {@code @Component} on the same
 * interface and setting {@code payment.provider=...} accordingly.
 */
@Component
@ConditionalOnProperty(name = "payment.provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentProvider implements PaymentProvider {

    @Override
    public ProviderPayment createPayment(UUID paymentId, BigDecimal amount, String currency) {
        String providerPaymentId = "mock-pay-" + paymentId;
        return new ProviderPayment(providerPaymentId, "PENDING",
                "https://payments.example.test/" + providerPaymentId);
    }

    @Override
    public ProviderPayment markSucceeded(String providerPaymentId) {
        return new ProviderPayment(providerPaymentId, "SUCCEEDED", null);
    }

    @Override
    public ProviderPayment markFailed(String providerPaymentId, String reason) {
        return new ProviderPayment(providerPaymentId, "FAILED", null);
    }
}
