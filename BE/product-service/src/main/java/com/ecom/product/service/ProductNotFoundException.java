package com.ecom.product.service;

import com.ecom.common.web.NotFoundException;

import java.util.UUID;

public class ProductNotFoundException extends NotFoundException {
    public ProductNotFoundException(UUID productId) {
        super("Product", productId);
    }
}
