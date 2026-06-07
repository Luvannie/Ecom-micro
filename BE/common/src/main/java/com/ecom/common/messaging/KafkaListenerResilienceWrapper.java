package com.ecom.common.messaging;

import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

@Component
public class KafkaListenerResilienceWrapper {
    private static final Logger log = LoggerFactory.getLogger(KafkaListenerResilienceWrapper.class);

    private final TimeLimiter timeLimiter;
    private final ScheduledExecutorService scheduler;

    public KafkaListenerResilienceWrapper() {
        this(TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(5))
                .cancelRunningFuture(true)
                .build());
    }

    KafkaListenerResilienceWrapper(TimeLimiterConfig config) {
        this.timeLimiter = TimeLimiter.of("kafkaConsumer", config);
        this.scheduler = Executors.newScheduledThreadPool(
                Runtime.getRuntime().availableProcessors());
    }

    public <T> T execute(Callable<T> handler, Consumer<Throwable> onTimeout) {
        java.util.function.Supplier<CompletionStage<T>> supplier = () ->
            CompletableFuture.supplyAsync(() -> {
                try {
                    return handler.call();
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        var decorated = Decorators
            .ofCompletionStage(supplier)
            .withTimeLimiter(timeLimiter, scheduler)
            .withFallback(java.util.List.of(TimeoutException.class),
                e -> {
                    onTimeout.accept(e);
                    throw new AmqpRejectAndDontRequeueException(
                        "kafka handler timeout (5s) — will be retried by KafkaErrorHandler", e);
                })
            .decorate();
        try {
            return decorated.get().toCompletableFuture().join();
        } catch (java.util.concurrent.CompletionException ce) {
            // Unwrap so consumers (Kafka listeners) see the original AmqpRejectAndDontRequeueException
            Throwable cause = ce.getCause();
            if (cause instanceof RuntimeException re) throw re;
            if (cause instanceof Error err) throw err;
            throw ce;
        }
    }
}
