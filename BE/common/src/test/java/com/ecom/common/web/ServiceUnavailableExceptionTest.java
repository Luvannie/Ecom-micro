package com.ecom.common.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceUnavailableExceptionTest {

    @Test
    void constructor_setsDownstreamServiceAndCause() {
        IOException cause = new IOException("connection refused");
        ServiceUnavailableException ex = new ServiceUnavailableException("product-service", cause);

        assertThat(ex.getDownstreamService()).isEqualTo("product-service");
        assertThat(ex.getCauseException()).isSameAs(cause);
        assertThat(ex.getMessage()).contains("product-service");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void exceptionIsRuntimeException() {
        ServiceUnavailableException ex = new ServiceUnavailableException("x", new IOException());
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}
