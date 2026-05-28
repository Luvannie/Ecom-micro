package com.ecom.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MockEmailSender {
    private static final Logger log = LoggerFactory.getLogger(MockEmailSender.class);

    public void send(String recipient, String subject, String body) {
        log.info("Mock email to={} subject={} body={}", recipient, subject, body);
    }
}
