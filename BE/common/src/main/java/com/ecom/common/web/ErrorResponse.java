package com.ecom.common.web;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        String code,
        String message,
        List<String> details,
        String correlationId,
        Instant timestamp
) {
    public static ErrorResponse of(String code, String message, List<String> details, String correlationId, Instant timestamp) {
        return new ErrorResponse(code, message, details, correlationId, timestamp);
    }
}
