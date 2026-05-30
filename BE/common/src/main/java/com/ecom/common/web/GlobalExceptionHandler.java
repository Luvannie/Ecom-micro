package com.ecom.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler providing consistent error responses across all services.
 * Must be imported by each service's component scan.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Correlation ID header name - must match CorrelationIdFilter
    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.toList());

        ErrorResponse body = ErrorResponse.of(
                "VALIDATION_ERROR",
                "Request validation failed",
                details,
                getCorrelationId(request),
                java.time.Instant.now()
        );

        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex,
                                                                 HttpServletRequest request) {
        log.warn("IllegalArgumentException: {} | path={}", ex.getMessage(), request.getRequestURI());

        ErrorResponse body = ErrorResponse.of(
                "BAD_REQUEST",
                ex.getMessage(),
                List.of(),
                getCorrelationId(request),
                java.time.Instant.now()
        );

        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<ErrorResponse> handleNullPointer(NullPointerException ex,
                                                            HttpServletRequest request) {
        log.error("NullPointerException: {} | path={}", ex.getMessage(), request.getRequestURI(), ex);

        ErrorResponse body = ErrorResponse.of(
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                List.of(),
                getCorrelationId(request),
                java.time.Instant.now()
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAll(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception: {} | path={}", ex.getMessage(), request.getRequestURI(), ex);

        ErrorResponse body = ErrorResponse.of(
                "INTERNAL_ERROR",
                "An unexpected error occurred. Please try again later.",
                List.of(),
                getCorrelationId(request),
                java.time.Instant.now()
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private String getCorrelationId(HttpServletRequest request) {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        return correlationId != null ? correlationId : "unknown";
    }
}