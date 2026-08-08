package com.ecom.order.service;

import com.ecom.common.web.ConflictException;

public class EmptyCartException extends ConflictException {
    public EmptyCartException() {
        super("Cart is empty");
    }
}
