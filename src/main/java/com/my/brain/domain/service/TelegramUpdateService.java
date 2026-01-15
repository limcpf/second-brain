package com.my.brain.domain.service;

import com.my.brain.domain.model.TelegramIncomingMessage;
import com.my.brain.domain.port.out.TelegramIncomingPublishPort;
import com.my.brain.domain.port.out.TelegramUpdatePort;

import java.util.List;

/**
 * 왜: 텔레그램 업데이트 조회와 후속 전달을 도메인 계층에서 조율해 중복/누락을 방지하기 위함.
 */
public class TelegramUpdateService {

    private final TelegramUpdatePort telegramUpdatePort;
    private final TelegramIncomingPublishPort telegramIncomingPublishPort;

    public TelegramUpdateService(TelegramUpdatePort telegramUpdatePort,
                                 TelegramIncomingPublishPort telegramIncomingPublishPort) {
        this.telegramUpdatePort = telegramUpdatePort;
        this.telegramIncomingPublishPort = telegramIncomingPublishPort;
    }

    public long fetchAndPublish(long offset, int timeoutSeconds) {
        List<TelegramIncomingMessage> updates = telegramUpdatePort.fetchUpdates(offset, timeoutSeconds);
        long nextOffset = offset;
        for (TelegramIncomingMessage update : updates) {
            telegramIncomingPublishPort.publish(update);
            nextOffset = Math.max(nextOffset, update.updateId() + 1);
        }
        return nextOffset;
    }
}
