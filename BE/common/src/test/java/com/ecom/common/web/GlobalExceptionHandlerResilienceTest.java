package com.ecom.common.web;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerResilienceTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final HttpServletRequest request = mock(HttpServletRequest.class);

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        when(request.getHeader("X-Correlation-Id")).thenReturn("corr-123");
        when(request.getRequestURI()).thenReturn("/api/cart/items");
    }

    @Test
    void serviceUnavailable_returns503WithCode() {
        ServiceUnavailableException ex = new ServiceUnavailableException("product-service", new IOException());

        ResponseEntity<ErrorResponse> response = handler.handleDownstreamUnavailable(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("5");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(response.getBody().correlationId()).isEqualTo("corr-123");
        assertThat(response.getBody().message()).contains("temporarily unavailable");
    }

    @Test
    void callNotPermitted_returns503() {
        CircuitBreaker cb = CircuitBreaker.of("test", CircuitBreakerConfig.custom()
            .slidingWindowSize(2).minimumNumberOfCalls(2).failureRateThreshold(50).build());
        // Force OPEN
        cb.transitionToOpenState();
        CallNotPermittedException ex = CallNotPermittedException.createCallNotPermittedException(cb);

        ResponseEntity<ErrorResponse> response = handler.handleDownstreamUnavailable(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void bulkheadFull_returns503() {
        BulkheadFullException ex = BulkheadFullException.createBulkheadFullException(
            io.github.resilience4j.bulkhead.Bulkhead.ofDefaults("test"));

        ResponseEntity<ErrorResponse> response = handler.handleDownstreamUnavailable(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void ioException_returns503() {
        IOException ex = new IOException("connection timeout");

        ResponseEntity<ErrorResponse> response = handler.handleDownstreamUnavailable(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void requestNotPermitted_returns429() {
        RateLimiter limiter = RateLimiter.of("test", RateLimiterConfig.custom()
            .limitForPeriod(5).limitRefreshPeriod(Duration.ofMinutes(1)).timeoutDuration(Duration.ZERO).build());
        RequestNotPermitted ex = RequestNotPermitted.createRequestNotPermitted(limiter);

        ResponseEntity<ErrorResponse> response = handler.handleRateLimit(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("60");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("RATE_LIMITED");
    }
}
