package com.my.brain.domain.exception;

/**
 * 왜: LLM 결과가 유효하지 않을 때 도메인에서 명시적으로 실패를 표현해 상위 계층이 대응하도록 하기 위함.
 */
public class IntentParseException extends RuntimeException {
    public IntentParseException(String message) {
        super(message);
    }

    public IntentParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
