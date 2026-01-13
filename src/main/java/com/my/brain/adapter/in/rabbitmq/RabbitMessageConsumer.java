package com.my.brain.adapter.in.rabbitmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.my.brain.adapter.in.idempotency.IdempotencyStore;
import com.my.brain.domain.exception.InvalidRequestException;
import com.my.brain.domain.model.BrainRequest;
import com.my.brain.domain.port.in.ProcessMessageUseCase;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.annotations.Blocking;
import io.smallrye.reactive.messaging.rabbitmq.IncomingRabbitMQMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import java.io.IOException;
import java.util.Optional;

/**
 * 왜: RabbitMQ 소비자를 통해 도메인 유스케이스로 진입시키는 단일 경로를 제공하기 위함.
 */
@ApplicationScoped
public class RabbitMessageConsumer {

    private static final Logger log = Logger.getLogger(RabbitMessageConsumer.class);

    private final ProcessMessageUseCase processMessageUseCase;
    private final IdempotencyStore idempotencyStore;
    private final ObjectMapper objectMapper;

    @Inject
    public RabbitMessageConsumer(ProcessMessageUseCase processMessageUseCase,
                                 IdempotencyStore idempotencyStore,
                                 ObjectMapper objectMapper) {
        this.processMessageUseCase = processMessageUseCase;
        this.idempotencyStore = idempotencyStore;
        this.objectMapper = objectMapper;
    }

    @Incoming("brain-requests")
    @Blocking
    public Uni<Void> consume(Message<String> message) {
        return Uni.createFrom().item(() -> {
            String payload = message.getPayload();
            BrainRequest request;
            try {
                IncomingRequest incoming = objectMapper.readValue(payload, IncomingRequest.class);
                request = incoming.toBrainRequest();
            } catch (IOException | InvalidRequestException e) {
                log.warnf("요청 파싱 실패로 처리 중단: %s", e.getMessage());
                return null;
            }
            String correlationId = resolveCorrelationId(message).orElse(request.eventId());
            MDC.put("correlationId", correlationId);
            MDC.put("eventId", request.eventId());
            try {
                if (idempotencyStore.isProcessed(request.eventId())) {
                    log.infof("중복 요청을 건너뜁니다: %s", request.eventId());
                    return null;
                }
                processMessageUseCase.process(request);
                idempotencyStore.markProcessed(request.eventId());
            } catch (InvalidRequestException e) {
                log.warnf("요청 검증 실패로 처리 중단: %s", e.getMessage());
            } finally {
                MDC.remove("correlationId");
                MDC.remove("eventId");
            }
            return null;
        }).replaceWithVoid();
    }

    private Optional<String> resolveCorrelationId(Message<String> message) {
        return message.getMetadata(IncomingRabbitMQMetadata.class)
                .flatMap(IncomingRabbitMQMetadata::getCorrelationId);
    }
}
