package com.my.brain.domain.port.in;

import com.my.brain.domain.model.TelegramOutgoingMessage;

/**
 * 왜: 외부 발신 요청을 도메인 단일 진입점으로 수렴시켜 텔레그램 전송 정책을 통제하기 위함.
 */
public interface RelayTelegramMessageUseCase {
    void relay(TelegramOutgoingMessage message);
}
