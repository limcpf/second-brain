package com.my.brain.domain.port.out;

import com.my.brain.domain.model.ReplyMessage;

/**
 * 왜: 응답 채널(RabbitMQ 등) 세부 구현을 숨기고 도메인이 단일 계약으로 응답을 요청하도록 하기 위함.
 */
public interface ReplyPort {
    void send(ReplyMessage replyMessage);
}
