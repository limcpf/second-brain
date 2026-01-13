package com.my.brain.domain.model;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * 왜: 외부로부터 수신한 원시 메시지의 최소 계약을 강제하여 이후 파이프라인에서 일관된 데이터 형태를 보장하기 위함.
 */
public record BrainRequest(
        String eventId,
        OffsetDateTime timestamp,
        String userId,
        MessageType type,
        String content
) {
    public BrainRequest {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(content, "content");
    }
}
