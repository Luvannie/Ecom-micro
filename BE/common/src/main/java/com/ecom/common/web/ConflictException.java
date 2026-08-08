package com.ecom.common.web;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an operation cannot be completed because the target
 * state conflicts with the current state. Maps to HTTP 409 with the
 * error code {@code CONFLICT}.
 */
public class ConflictException extends DomainException {
    public ConflictException(String message) {
        super("CONFLICT", HttpStatus.CONFLICT, message);
    }
}
