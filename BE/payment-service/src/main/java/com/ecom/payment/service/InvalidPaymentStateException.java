package com.ecom.payment.service;

import com.ecom.common.web.ConflictException;

public class InvalidPaymentStateException extends ConflictException {
    public InvalidPaymentStateException(String message) {
        super(message);
    }
}
