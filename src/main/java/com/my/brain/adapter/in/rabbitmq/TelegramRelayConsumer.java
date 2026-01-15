package com.my.brain.adapter.in.rabbitmq;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.my.brain.domain.model.TelegramOutgoingMessage;
import com.my.brain.domain.port.in.RelayTelegramMessageUseCase;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

/**
 * 왜: 타 서비스가 RabbitMQ로 전달한 메시지를 텔레그램으로 중계하는 진입 어댑터가 필요하기 때문.
 */
@ApplicationScoped
public class TelegramRelayConsumer {

    private static final Logger log = Logger.getLogger(TelegramRelayConsumer.class);

    private final RelayTelegramMessageUseCase relayTelegramMessageUseCase;
    private final ObjectMapper objectMapper;

    @Inject
    public TelegramRelayConsumer(RelayTelegramMessageUseCase relayTelegramMessageUseCase, ObjectMapper objectMapper) {
        this.relayTelegramMessageUseCase = relayTelegramMessageUseCase;
        this.objectMapper = objectMapper;
    }

    @Incoming("telegram-outgoing")
    @Blocking
    public Uni<Void> consume(Message<String> message) {
        return Uni.createFrom().item(() -> {
            String payload = message.getPayload();
            try {
                OutgoingTelegramPayload outgoing = objectMapper.readValue(payload, OutgoingTelegramPayload.class);
                relayTelegramMessageUseCase.relay(outgoing.toDomain());
            } catch (Exception e) {
                log.warnf("텔레그램 전송 요청 파싱/처리 실패: %s", e.getMessage());
            }
            return null;
        }).replaceWithVoid();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OutgoingTelegramPayload(long chatId, String text, String parseMode) {
        TelegramOutgoingMessage toDomain() {
            return new TelegramOutgoingMessage(chatId, text, parseMode);
        }
    }
}
