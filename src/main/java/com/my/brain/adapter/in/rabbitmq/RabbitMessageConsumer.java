package com.my.brain.adapter.in.rabbitmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.my.brain.domain.model.BrainRequest;
import com.my.brain.domain.model.MessageType;
import com.my.brain.domain.port.in.ProcessMessageUseCase;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;

/**
 * 왜: RabbitMQ 소비자를 통해 도메인 유스케이스로 진입시키는 단일 경로를 제공하기 위함.
 */
@ApplicationScoped
public class RabbitMessageConsumer {

    private final ProcessMessageUseCase processMessageUseCase;
    private final ObjectMapper objectMapper;

    @Inject
    public RabbitMessageConsumer(ProcessMessageUseCase processMessageUseCase) {
        this.processMessageUseCase = processMessageUseCase;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Incoming("brain-requests")
    @Blocking
    public void consume(String payload) throws IOException {
        BrainRequest request = mapToRequest(payload);
        processMessageUseCase.process(request);
    }

    private BrainRequest mapToRequest(String payload) throws IOException {
        Map<String, Object> map = objectMapper.readValue(payload, Map.class);
        String eventId = (String) map.get("eventId");
        String ts = (String) map.get("timestamp");
        String userId = (String) map.get("userId");
        String type = (String) map.get("type");
        String content = (String) map.get("content");
        OffsetDateTime timestamp = OffsetDateTime.parse(ts).withOffsetSameInstant(ZoneId.of("Asia/Seoul").getRules().getOffset(OffsetDateTime.now()));
        return new BrainRequest(eventId, timestamp, userId, MessageType.valueOf(type), content);
    }
}
