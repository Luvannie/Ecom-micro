package com.ecom.payment.outbox;

import com.ecom.payment.domain.OutboxEvent;
import com.ecom.payment.domain.OutboxStatus;
import com.ecom.payment.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxPublisherTest {
    private OutboxEventRepository repository;
    private KafkaTemplate<Object, Object> kafkaTemplate;
    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(OutboxEventRepository.class);
        kafkaTemplate = Mockito.mock(KafkaTemplate.class);
        publisher = new OutboxPublisher(repository, kafkaTemplate);
    }

    @Test
    void marks_event_published_after_successful_send() {
        OutboxEvent event = new OutboxEvent("Payment", UUID.randomUUID(), "payment.succeeded", "{}");
        when(repository.lockTopPending(eq(OutboxStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(event));
        when(repository.findById(event.getId())).thenReturn(java.util.Optional.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(completedFuture(mock(SendResult.class)));

        publisher.publishPending();

        verify(repository).save(argThat(e -> e.getStatus() == OutboxStatus.PUBLISHED));
    }

    @Test
    void does_not_save_on_send_failure() {
        OutboxEvent event = new OutboxEvent("Payment", UUID.randomUUID(), "payment.succeeded", "{}");
        when(repository.lockTopPending(eq(OutboxStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(event));
        when(repository.findById(event.getId())).thenReturn(java.util.Optional.of(event));
        CompletableFuture<SendResult<Object, Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("kafka down"));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failed);

        publisher.publishPending();

        verify(repository).save(argThat(e -> e.getStatus() == OutboxStatus.FAILED));
    }
}
