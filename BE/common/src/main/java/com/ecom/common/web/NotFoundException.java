package com.ecom.common.web;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a requested resource does not exist. Maps to HTTP 404
 * with the error code {@code NOT_FOUND}.
 */
public class NotFoundException extends DomainException {
    public NotFoundException(String resource, Object id) {
        super("NOT_FOUND", HttpStatus.NOT_FOUND, resource + " " + id + " not found");
    }

    public NotFoundException(String message) {
        super("NOT_FOUND", HttpStatus.NOT_FOUND, message);
    }
}
