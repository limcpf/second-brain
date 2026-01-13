package com.my.brain.domain.model;

/**
 * 왜: LLM이 분류한 사용자 의도를 명확히 표현하여 유스케이스 분기 기준을 단순화하기 위함.
 */
public enum IntentType {
    CALENDAR,
    TASK,
    NOTE,
    SYNC,
    UNKNOWN
}
