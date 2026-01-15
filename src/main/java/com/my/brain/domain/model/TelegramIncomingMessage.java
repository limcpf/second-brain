package com.my.brain.domain.model;

import java.util.Objects;

/**
 * 왜: 텔레그램에서 수신한 원시 메시지의 최소 필드를 고정해 후속 처리/전달 시 일관성을 보장하기 위함.
 */
public record TelegramIncomingMessage(long updateId, long chatId, String from, String text, long epochSeconds) {

    public TelegramIncomingMessage {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(from, "from");
        if (text.isBlank()) {
            throw new IllegalArgumentException("text는 비어 있을 수 없습니다.");
        }
    }
}
