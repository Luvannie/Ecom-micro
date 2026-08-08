package com.ecom.payment.outbox;

import com.ecom.payment.domain.OutboxEvent;
import com.ecom.payment.domain.OutboxStatus;
import com.ecom.payment.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int BATCH_SIZE = 100;

    private final OutboxEventRepository repository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    public OutboxPublisher(OutboxEventRepository repository, KafkaTemplate<Object, Object> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Lock a batch of pending events under SKIP LOCKED so this publisher
     * instance can work them without colliding with other publisher nodes.
     */
    @Transactional
    protected List<UUID> lockAndCollectIds() {
        return repository.lockTopPending(OutboxStatus.PENDING, PageRequest.of(0, BATCH_SIZE))
                .stream()
                .map(OutboxEvent::getId)
                .toList();
    }

    @Scheduled(fixedDelayString = "${payment.outbox.publish-fixed-delay-ms:5000}")
    public void publishPending() {
        for (UUID id : lockAndCollectIds()) {
            publishOne(id);
        }
    }

    private void publishOne(UUID eventId) {
        OutboxEvent event = repository.findById(eventId).orElse(null);
        if (event == null || event.getStatus() != OutboxStatus.PENDING) {
            return; // already taken by another node, or gone
        }
        try {
            CompletableFuture<SendResult<Object, Object>> future = kafkaTemplate.send(
                    event.getEventType(),
                    event.getAggregateId().toString(),
                    event.getPayload());
            future.whenComplete((result, ex) -> updateStatus(eventId, ex == null ? OutboxStatus.PUBLISHED : OutboxStatus.FAILED));
        } catch (Exception ex) {
            log.warn("Outbox send failed for event {}: {}", eventId, ex.getMessage());
            updateStatus(eventId, OutboxStatus.FAILED);
        }
    }

    @Transactional
    protected void updateStatus(UUID eventId, OutboxStatus status) {
        repository.findById(eventId).ifPresent(e -> {
            if (e.getStatus() == OutboxStatus.PENDING) {
                if (status == OutboxStatus.PUBLISHED) {
                    e.markPublished();
                } else {
                    e.markFailed();
                }
                repository.save(e);
            }
        });
    }
}
