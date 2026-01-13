package com.my.brain.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * 왜: 캘린더 등록 시 필요한 핵심 필드를 단일 구조로 묶어 검증과 API 매핑을 단순화하기 위함.
 */
public record CalendarEvent(
        String summary,
        TimeRange timeRange,
        String location,
        List<String> attendees,
        String description,
        Note linkedNote
) {
    public CalendarEvent {
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(timeRange, "timeRange");
        if (summary.isBlank()) {
            throw new IllegalArgumentException("이벤트 제목은 비어 있을 수 없습니다.");
        }
        attendees = attendees == null ? List.of() : List.copyOf(attendees);
    }
}
