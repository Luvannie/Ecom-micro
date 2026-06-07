package com.ecom.common.web;

public class ServiceUnavailableException extends RuntimeException {
    private final String downstreamService;

    public ServiceUnavailableException(String downstreamService, Throwable cause) {
        super("Downstream service '" + downstreamService + "' is unavailable", cause);
        this.downstreamService = downstreamService;
    }

    public String getDownstreamService() {
        return downstreamService;
    }

    public Throwable getCauseException() {
        return getCause();
    }
}
