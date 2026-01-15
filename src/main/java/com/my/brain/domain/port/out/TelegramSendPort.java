package com.my.brain.domain.port.out;

import com.my.brain.domain.model.TelegramOutgoingMessage;

/**
 * 왜: 텔레그램 전송 구현을 도메인에서 분리해 환경/전송 방식 변경 시에도 계약을 유지하기 위함.
 */
public interface TelegramSendPort {
    void send(TelegramOutgoingMessage message);
}
