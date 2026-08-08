package com.ecom.cart.service;

import com.ecom.common.web.ConflictException;

import java.util.UUID;

public class ProductUnavailableException extends ConflictException {
    public ProductUnavailableException(UUID productId) {
        super("Product " + productId + " is not available");
    }
}
