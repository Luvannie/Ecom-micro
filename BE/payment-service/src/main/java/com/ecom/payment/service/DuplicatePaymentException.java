package com.ecom.payment.service;

import java.util.UUID;

public class DuplicatePaymentException extends RuntimeException {
    public DuplicatePaymentException(UUID orderId) {
        super("Payment already exists for order: " + orderId);
    }
}
