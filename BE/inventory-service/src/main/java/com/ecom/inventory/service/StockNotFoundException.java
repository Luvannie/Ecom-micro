package com.ecom.inventory.service;

import com.ecom.common.web.NotFoundException;

import java.util.UUID;

public class StockNotFoundException extends NotFoundException {
    public StockNotFoundException(UUID productId) {
        super("Stock", productId);
    }
}
