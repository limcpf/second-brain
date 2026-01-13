package com.my.brain.domain.model;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * 왜: 할 일을 구조화하여 마감일 검증과 외부 API 매핑을 단순화하기 위함.
 */
public record TodoItem(String description, OffsetDateTime dueDate) {
    public TodoItem {
        Objects.requireNonNull(description, "description");
        if (description.isBlank()) {
            throw new IllegalArgumentException("할 일 내용은 비어 있을 수 없습니다.");
        }
    }
}
