package com.ecom.common.web;

import org.springframework.http.HttpStatus;

/**
 * Base class for all domain-level exceptions in the ecom platform.
 *
 * <p>A {@code DomainException} carries both an HTTP status (used by
 * {@link GlobalExceptionHandler} to set the response status) and a stable
 * machine-readable code (returned in the {@code code} field of the
 * {@link ErrorResponse} body). Service-specific exceptions should
 * subclass one of the ready-made {@link NotFoundException},
 * {@link ConflictException}, or {@link BadRequestException} for common
 * cases, or extend this class directly when a different status is
 * required.
 */
public abstract class DomainException extends RuntimeException {

    private final String code;
    private final HttpStatus httpStatus;

    protected DomainException(String code, HttpStatus httpStatus, String message) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
