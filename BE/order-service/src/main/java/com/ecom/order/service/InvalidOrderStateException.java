package com.ecom.order.service;

import com.ecom.common.web.ConflictException;

public class InvalidOrderStateException extends ConflictException {
    public InvalidOrderStateException(String message) {
        super(message);
    }
}
