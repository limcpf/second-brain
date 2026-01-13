package com.my.brain.domain.port.out;

import com.my.brain.domain.model.ReplyMessage;

/**
 * 왜: 응답 채널을 식별하기 위한 마커 인터페이스로 어댑터 구현 시 의도를 명확히 하기 위함.
 */
public interface ReplyChannel {
    void publish(ReplyMessage replyMessage);
}
