package com.ecom.payment.service;

import com.ecom.common.web.NotFoundException;

public class PaymentNotFoundException extends NotFoundException {
    public PaymentNotFoundException(Object id) {
        super("Payment not found: " + id);
    }
}
