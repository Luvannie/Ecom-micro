package com.ecom.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DomainExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final HttpServletRequest request = mock(HttpServletRequest.class);

    @BeforeEach
    void setUp() {
        when(request.getHeader("X-Correlation-Id")).thenReturn("corr-123");
        when(request.getRequestURI()).thenReturn("/api/test");
    }

    @Test
    void notFound() {
        NotFoundException ex = new NotFoundException("Order", "42");

        ResponseEntity<ErrorResponse> response = handler.handleDomain(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
        assertThat(response.getBody().message()).isEqualTo("Order 42 not found");
    }

    @Test
    void conflict() {
        ConflictException ex = new ConflictException("Already exists");

        ResponseEntity<ErrorResponse> response = handler.handleDomain(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("CONFLICT");
    }

    @Test
    void badRequest() {
        BadRequestException ex = new BadRequestException("Bad input");

        ResponseEntity<ErrorResponse> response = handler.handleDomain(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("BAD_REQUEST");
    }
}
