package com.ecom.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MockSmsSender {
    private static final Logger log = LoggerFactory.getLogger(MockSmsSender.class);

    public void send(String recipient, String message) {
        log.info("Mock SMS to={} message={}", recipient, message);
    }
}
