package com.ecom.payment.outbox;

import com.ecom.payment.domain.OutboxEvent;
import com.ecom.payment.domain.OutboxStatus;
import com.ecom.payment.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
    void publisherSendsEventToTopicAndMarksPublished() {
        OutboxEvent event = new OutboxEvent("Payment", UUID.randomUUID(), "payment.succeeded", "{\"ok\":true}");
        when(repository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING)).thenReturn(List.of(event));
        when(repository.save(event)).thenReturn(event);

        publisher.publishPending();

        verify(kafkaTemplate).send("payment.succeeded", event.getAggregateId().toString(), event.getPayload());
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    }

    @Test
    void publisherMarksFailedWhenKafkaSendThrows() {
        OutboxEvent event = new OutboxEvent("Payment", UUID.randomUUID(), "payment.failed", "{\"ok\":false}");
        when(repository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING)).thenReturn(List.of(event));
        when(kafkaTemplate.send("payment.failed", event.getAggregateId().toString(), event.getPayload()))
                .thenThrow(new RuntimeException("broker unavailable"));

        publisher.publishPending();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        verify(repository).save(event);
    }
}
