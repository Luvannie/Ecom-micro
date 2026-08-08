package com.ecom.order.service;

import com.ecom.common.web.NotFoundException;

import java.util.UUID;

public class OrderNotFoundException extends NotFoundException {
    public OrderNotFoundException(UUID orderId) {
        super("Order", orderId);
    }
}
