package com.my.brain.domain.port.out;

import com.my.brain.domain.model.TelegramIncomingMessage;

/**
 * 왜: 텔레그램 수신 메시지의 후속 전달 채널(RabbitMQ 등)을 추상화해 교체 가능성을 확보하기 위함.
 */
public interface TelegramIncomingPublishPort {
    void publish(TelegramIncomingMessage message);
}
