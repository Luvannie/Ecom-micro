package com.ecom.payment.outbox;

import com.ecom.payment.domain.OutboxEvent;
import com.ecom.payment.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OutboxService {
    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * @deprecated use {@link #append(String, UUID, String, com.ecom.payment.messaging.event.PaymentEvent)}
     *             instead. The typed overload is the preferred API for new callers; this overload is
     *             retained for backward compatibility while other code paths (e.g. webhook processing)
     *             are migrated.
     */
    @Deprecated(forRemoval = true)
    @Transactional
    public OutboxEvent append(String aggregateType, UUID aggregateId, String eventType, Object payload) {
        try {
            return repository.save(new OutboxEvent(aggregateType, aggregateId, eventType,
                    objectMapper.writeValueAsString(payload)));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not serialize outbox payload", exception);
        }
    }

    @Transactional
    public OutboxEvent append(String aggregateType, UUID aggregateId, String eventType,
                              com.ecom.payment.messaging.event.PaymentEvent event) {
        try {
            return repository.save(new OutboxEvent(aggregateType, aggregateId, eventType,
                    objectMapper.writeValueAsString(event)));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not serialize outbox payload", exception);
        }
    }

    @Transactional
    public void markPublished(UUID eventId) {
        repository.findById(eventId).ifPresent(event -> {
            event.markPublished();
            repository.save(event);
        });
    }

    @Transactional
    public void markFailed(UUID eventId) {
        repository.findById(eventId).ifPresent(event -> {
            event.markFailed();
            repository.save(event);
        });
    }
}
