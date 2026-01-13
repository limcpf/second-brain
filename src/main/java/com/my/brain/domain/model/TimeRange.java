package com.my.brain.domain.model;

import java.time.OffsetDateTime;

/**
 * 왜: 일정 처리 시 시작/종료 시간을 한 덩어리로 검증하고 전달하기 위함.
 */
public record TimeRange(OffsetDateTime start, OffsetDateTime end) {
    public TimeRange {
        if (start != null && end != null && end.isBefore(start)) {
            throw new IllegalArgumentException("종료 시간이 시작 시간보다 이를 수 없습니다.");
        }
    }
}
