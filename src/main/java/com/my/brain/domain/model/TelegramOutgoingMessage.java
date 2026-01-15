package com.my.brain.domain.model;

import java.util.Objects;

/**
 * 왜: 텔레그램 전송 메시지 계약을 고정해 어댑터 간 포맷 불일치를 방지하기 위함.
 */
public record TelegramOutgoingMessage(long chatId, String text, String parseMode) {

    public TelegramOutgoingMessage {
        Objects.requireNonNull(text, "text");
        if (text.isBlank()) {
            throw new IllegalArgumentException("text는 비어 있을 수 없습니다.");
        }
    }

    public TelegramOutgoingMessage(long chatId, String text) {
        this(chatId, text, null);
    }
}
