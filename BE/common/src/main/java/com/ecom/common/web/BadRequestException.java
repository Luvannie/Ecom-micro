package com.ecom.common.web;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a request is malformed or violates a business rule that
 * should be reported as 400. Maps to HTTP 400 with the error code
 * {@code BAD_REQUEST}.
 */
public class BadRequestException extends DomainException {
    public BadRequestException(String message) {
        super("BAD_REQUEST", HttpStatus.BAD_REQUEST, message);
    }
}
