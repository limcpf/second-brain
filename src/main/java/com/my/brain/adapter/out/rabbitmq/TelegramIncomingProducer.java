package com.my.brain.adapter.out.rabbitmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.my.brain.domain.model.TelegramIncomingMessage;
import com.my.brain.domain.port.out.TelegramIncomingPublishPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.logging.Logger;

/**
 * 왜: 텔레그램 수신 메시지를 RabbitMQ로 전달하는 드리븐 어댑터를 분리해 전달 경로를 명확히 하기 위함.
 */
@ApplicationScoped
public class TelegramIncomingProducer implements TelegramIncomingPublishPort {

    private static final Logger log = Logger.getLogger(TelegramIncomingProducer.class);

    private final Emitter<String> emitter;
    private final ObjectMapper objectMapper;

    @Inject
    public TelegramIncomingProducer(@Channel("telegram-incoming") Emitter<String> emitter,
                                    ObjectMapper objectMapper) {
        this.emitter = emitter;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(TelegramIncomingMessage message) {
        try {
            String payload = objectMapper.writeValueAsString(new OutgoingPayload(message));
            emitter.send(payload);
        } catch (Exception e) {
            log.warnf("텔레그램 수신 메시지 직렬화 실패: %s", e.getMessage());
        }
    }

    private record OutgoingPayload(long updateId, long chatId, String from, String text, long epochSeconds) {
        private OutgoingPayload(TelegramIncomingMessage message) {
            this(message.updateId(), message.chatId(), message.from(), message.text(), message.epochSeconds());
        }
    }
}
