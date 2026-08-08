package com.ecom.common.web;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
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

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomain(DomainException ex, HttpServletRequest request) {
        log.warn("Domain exception: code={} status={} path={} message={}",
                ex.getCode(), ex.getHttpStatus().value(), request.getRequestURI(), ex.getMessage());
        ErrorResponse body = ErrorResponse.of(
                ex.getCode(),
                ex.getMessage(),
                List.of(),
                getCorrelationId(request),
                java.time.Instant.now()
        );
        return ResponseEntity.status(ex.getHttpStatus()).body(body);
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

    @ExceptionHandler({ServiceUnavailableException.class, CallNotPermittedException.class,
                       BulkheadFullException.class, java.io.IOException.class})
    public ResponseEntity<ErrorResponse> handleDownstreamUnavailable(Exception ex, HttpServletRequest request) {
        String downstream = ex instanceof ServiceUnavailableException sue
            ? sue.getDownstreamService() : "unknown";
        log.warn("Downstream unavailable: {} | path={} | reason={}",
                 downstream, request.getRequestURI(), ex.getMessage());
        ErrorResponse body = ErrorResponse.of(
            "SERVICE_UNAVAILABLE",
            "Downstream service temporarily unavailable. Please retry.",
            List.of(),
            getCorrelationId(request),
            java.time.Instant.now()
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                             .header("Retry-After", "5")
                             .body(body);
    }

    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(RequestNotPermitted ex, HttpServletRequest request) {
        log.warn("Rate limit exceeded: {} | path={}", ex.getMessage(), request.getRequestURI());
        ErrorResponse body = ErrorResponse.of(
            "RATE_LIMITED",
            "Rate limit exceeded. Please slow down.",
            List.of(),
            getCorrelationId(request),
            java.time.Instant.now()
        );
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                             .header("Retry-After", "60")
                             .body(body);
    }

    private String getCorrelationId(HttpServletRequest request) {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        return correlationId != null ? correlationId : "unknown";
    }
}