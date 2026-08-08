package com.ecom.payment.provider;

/**
 * The view of a payment as known by the external provider. Adapter-specific
 * fields (provider URLs, gateway IDs, ...) belong on the adapter, not on
 * the interface contract.
 */
public record ProviderPayment(String providerPaymentId, String status, String redirectUrl) {
}
