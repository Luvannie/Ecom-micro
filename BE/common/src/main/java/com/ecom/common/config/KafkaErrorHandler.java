package com.ecom.common.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Global Kafka error handler configuration.
 * Provides retry logic with 3 attempts and 1 second interval.
 */
@Configuration
public class KafkaErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(KafkaErrorHandler.class);
    private static final long RETRY_INTERVAL_MS = 1000;
    private static final long MAX_ATTEMPTS = 3;

    /**
     * Creates a DefaultErrorHandler bean for Kafka error handling.
     * Configured with retry logic and logging for failed messages.
     */
    @Bean
    public DefaultErrorHandler defaultKafkaErrorHandler() {
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                (record, exception) -> {
                    // This runs after all retries are exhausted
                    log.error("Kafka message processing failed after {} attempts for topic={}, key={}, partition={}, offset={}: {}",
                            MAX_ATTEMPTS,
                            record.topic(),
                            record.key() != null ? record.key() : "null",
                            record.partition(),
                            record.offset(),
                            exception.getMessage(),
                            exception);
                },
                new FixedBackOff(RETRY_INTERVAL_MS, MAX_ATTEMPTS - 1)
        );

        // Log on each retry attempt
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) -> {
            log.warn("Kafka retry attempt {} for topic={}, key={}, partition={}, offset={}: {}",
                    deliveryAttempt,
                    record.topic(),
                    record.key() != null ? record.key() : "null",
                    record.partition(),
                    record.offset(),
                    ex.getMessage());
        });

        return errorHandler;
    }
}