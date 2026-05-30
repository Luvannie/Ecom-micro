package com.ecom.cart.service;

import java.util.UUID;

public class ProductUnavailableException extends RuntimeException {
    public ProductUnavailableException(UUID productId) {
        super("Product is unavailable: " + productId);
    }
}
