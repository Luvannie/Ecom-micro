package com.ecom.common.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KafkaListenerResilienceWrapperTest {

    private final KafkaListenerResilienceWrapper wrapper = new KafkaListenerResilienceWrapper();

    @Test
    void normalExecution_returnsResult() throws Exception {
        Callable<String> handler = () -> "hello";

        String result = wrapper.execute(handler, t -> {});

        assertThat(result).isEqualTo("hello");
    }

    @Test
    void timeout_throwsAmqpRejectAndDontRequeue() {
        Callable<String> slowHandler = () -> {
            Thread.sleep(6000);
            return "ok";
        };
        AtomicReference<Throwable> captured = new AtomicReference<>();

        assertThatThrownBy(() -> wrapper.execute(slowHandler, captured::set))
            .isInstanceOf(AmqpRejectAndDontRequeueException.class)
            .hasMessageContaining("timeout");
    }
}
