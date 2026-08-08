package com.ecom.cart.service;

import com.ecom.common.web.BadRequestException;

public class InvalidCartQuantityException extends BadRequestException {
    public InvalidCartQuantityException(int quantity) {
        super("Invalid cart quantity: " + quantity);
    }
}
