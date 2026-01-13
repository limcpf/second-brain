package com.my.brain.adapter.in.rabbitmq;

import io.smallrye.reactive.messaging.annotations.Blocking;
import io.smallrye.reactive.messaging.rabbitmq.IncomingRabbitMQMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;
import io.quarkus.arc.profile.IfBuildProfile;

import java.util.Optional;

/**
 * 왜: DLQ 적체를 가시화하고 재처리 훅을 제공하기 위해 별도 소비자를 둔다.
 */
@IfBuildProfile("prod")
@ApplicationScoped
public class DeadLetterConsumer {

    private static final Logger log = Logger.getLogger(DeadLetterConsumer.class);

    @Incoming("brain-requests-dlq")
    @Blocking
    public java.util.concurrent.CompletionStage<Void> consume(Message<String> message) {
        var metadata = message.getMetadata(IncomingRabbitMQMetadata.class).orElse(null);
        String payload = message.getPayload();
        String deathReason = Optional.ofNullable(metadata)
                .map(IncomingRabbitMQMetadata::getHeaders)
                .map(headers -> String.valueOf(headers.getOrDefault("x-first-death-reason", "unknown")))
                .orElse("unknown");
        String firstQueue = Optional.ofNullable(metadata)
                .map(IncomingRabbitMQMetadata::getHeaders)
                .map(headers -> String.valueOf(headers.getOrDefault("x-first-death-queue", "unknown")))
                .orElse("unknown");
        log.warnf("DLQ 소비: reason=%s, queue=%s, payload=%s", deathReason, firstQueue, payload);
        return message.ack();
    }
}
