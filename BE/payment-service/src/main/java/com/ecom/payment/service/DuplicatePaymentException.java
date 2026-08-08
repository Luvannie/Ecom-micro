package com.ecom.payment.service;

import com.ecom.common.web.ConflictException;

import java.util.UUID;

public class DuplicatePaymentException extends ConflictException {
    public DuplicatePaymentException(UUID orderId) {
        super("Payment already exists for order: " + orderId);
    }
}
