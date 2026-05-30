package com.ecom.payment.outbox;

import com.ecom.payment.domain.OutboxStatus;
import com.ecom.payment.repository.OutboxEventRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class OutboxPublisher {
    private final OutboxEventRepository repository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    public OutboxPublisher(OutboxEventRepository repository, KafkaTemplate<Object, Object> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${payment.outbox.publish-fixed-delay-ms:5000}")
    public void publishPending() {
        repository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING).forEach(event -> {
            CompletableFuture<SendResult<Object, Object>> future =
                    kafkaTemplate.send(event.getEventType(), event.getAggregateId().toString(), event.getPayload());
            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    event.markFailed();
                } else {
                    event.markPublished();
                }
                repository.save(event);
            });
        });
    }
}