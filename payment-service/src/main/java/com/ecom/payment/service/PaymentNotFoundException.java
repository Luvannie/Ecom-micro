package com.ecom.payment.service;

public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(Object id) {
        super("Payment not found: " + id);
    }
}
