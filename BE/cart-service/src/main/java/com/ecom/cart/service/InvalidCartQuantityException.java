package com.ecom.cart.service;

public class InvalidCartQuantityException extends RuntimeException {
    public InvalidCartQuantityException() {
        super("Quantity must be between 1 and 99");
    }
}
