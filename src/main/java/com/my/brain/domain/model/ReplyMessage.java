package com.my.brain.domain.model;

import java.util.Objects;

/**
 * 왜: 최종 응답 메시지의 계약을 고정하여 어댑터가 일관된 포맷으로 전송하도록 하기 위함.
 */
public record ReplyMessage(String replyToUserId, String content) {
    public ReplyMessage {
        Objects.requireNonNull(replyToUserId, "replyToUserId");
        Objects.requireNonNull(content, "content");
        if (replyToUserId.isBlank()) {
            throw new IllegalArgumentException("replyToUserId는 비어 있을 수 없습니다.");
        }
    }
}
