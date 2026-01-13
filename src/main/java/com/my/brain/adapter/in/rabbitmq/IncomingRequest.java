package com.my.brain.adapter.in.rabbitmq;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.my.brain.domain.exception.InvalidRequestException;
import com.my.brain.domain.model.BrainRequest;
import com.my.brain.domain.model.MessageType;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IncomingRequest(String eventId,
                              String timestamp,
                              String userId,
                              String type,
                              String content) {

    public IncomingRequest {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(content, "content");
        if (eventId.isBlank() || userId.isBlank() || content.isBlank()) {
            throw new InvalidRequestException("요청 필드가 비어 있습니다.");
        }
    }

    public BrainRequest toBrainRequest() {
        try {
            OffsetDateTime parsed = OffsetDateTime.parse(timestamp)
                    .atZoneSameInstant(ZoneId.of("Asia/Seoul"))
                    .toOffsetDateTime();
            return new BrainRequest(eventId, parsed, userId, MessageType.valueOf(type), content);
        } catch (DateTimeParseException | IllegalArgumentException e) {
            throw new InvalidRequestException("요청 필드 형식이 올바르지 않습니다.", e);
        }
    }
}
