package com.my.brain.domain.exception;

/**
 * 왜: 외부 입력이 계약을 위반했을 때 도메인 단계에서 명확히 실패를 알리기 위함.
 */
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }

    public InvalidRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
