package com.ecom.payment.provider;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Abstraction over an external payment provider (mock, Stripe, VNPay, ...).
 * Business code in {@code payment-service} depends on this interface;
 * the concrete adapter lives in this same package.
 */
public interface PaymentProvider {
    ProviderPayment createPayment(UUID paymentId, BigDecimal amount, String currency);
    ProviderPayment markSucceeded(String providerPaymentId);
    ProviderPayment markFailed(String providerPaymentId, String reason);
}
