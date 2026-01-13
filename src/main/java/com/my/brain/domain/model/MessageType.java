package com.my.brain.domain.model;

/**
 * 왜: 프로듀서가 구분한 메시지 유형을 명시적으로 표현하여 파이프라인 분기를 단순화하기 위함.
 */
public enum MessageType {
    CHAT,
    SYNC
}
