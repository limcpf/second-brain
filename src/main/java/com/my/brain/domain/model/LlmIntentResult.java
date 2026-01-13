package com.my.brain.domain.model;

import java.util.Objects;

/**
 * 왜: LLM이 반환한 구조화 결과를 도메인 내부 포맷으로 고정하여 후속 분기 로직을 단순화하기 위함.
 */
public record LlmIntentResult(IntentType intentType, CalendarEvent calendarEvent, TodoItem todoItem, Note note) {
    public LlmIntentResult {
        Objects.requireNonNull(intentType, "intentType");
    }
}
