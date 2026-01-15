package com.my.brain.domain.service;

import com.my.brain.domain.model.TelegramOutgoingMessage;
import com.my.brain.domain.port.in.RelayTelegramMessageUseCase;
import com.my.brain.domain.port.out.TelegramSendPort;

/**
 * 왜: 외부 발신 요청 처리 로직을 한 곳에서 검증 후 전송하도록 분리해 책임을 명확히 하기 위함.
 */
public class TelegramRelayService implements RelayTelegramMessageUseCase {

    private final TelegramSendPort telegramSendPort;

    public TelegramRelayService(TelegramSendPort telegramSendPort) {
        this.telegramSendPort = telegramSendPort;
    }

    @Override
    public void relay(TelegramOutgoingMessage message) {
        telegramSendPort.send(message);
    }
}
