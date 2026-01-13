package com.my.brain.adapter.out.reply;

import com.my.brain.domain.model.ReplyMessage;
import com.my.brain.domain.port.out.ReplyPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

/**
 * 왜: 도메인 응답을 RabbitMQ로 전달하는 기술적 구현을 분리하여 포트 계약을 지키기 위함.
 */
@ApplicationScoped
public class RabbitReplyProducer implements ReplyPort {

    private final Emitter<String> replyEmitter;

    @Inject
    public RabbitReplyProducer(@Channel("brain-replies") Emitter<String> replyEmitter) {
        this.replyEmitter = replyEmitter;
    }

    @Override
    public void send(ReplyMessage replyMessage) {
        // 단순 직렬화 (추후 ObjectMapper 주입 가능)
        String payload = "{\"replyToUserId\":\"" + replyMessage.replyToUserId() + "\",\"content\":\"" + replyMessage.content() + "\"}";
        replyEmitter.send(payload);
    }
}
