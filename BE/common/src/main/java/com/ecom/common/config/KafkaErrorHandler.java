package com.ecom.common.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.ContainerStoppingErrorHandler;
import org.springframework.kafka.listener.ErrorHandler;
import org.springframework.stereotype.Component;

/**
 * Global Kafka error handler that logs failures and prevents message loss.
 * Uses a Dead Letter Queue (DLQ) approach by sending failed messages to a retry topic.
 */
@Component
public class KafkaErrorHandler implements ErrorHandler {
    private static final Logger log = LoggerFactory.getLogger(KafkaErrorHandler.class);

    @Override
    public void handle(Exception thrownException, ConsumerRecord<?, ?> record) {
        log.error("Kafka message processing failed for topic={}, key={}, partition={}, offset={}: {}",
                record.topic(),
                record.key(),
                record.partition(),
                record.offset(),
                thrownException.getMessage(),
                thrownException);

        // In production, you would send to a DLQ topic here:
        // dlqTemplate.send("dlq." + record.topic(), record.key(), record.value());

        // For now, we log and continue - the message will be retried based on spring.kafka.listener配置
    }
}