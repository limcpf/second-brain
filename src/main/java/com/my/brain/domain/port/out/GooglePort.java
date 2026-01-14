package com.my.brain.domain.port.out;

import com.my.brain.domain.model.CalendarEvent;
import com.my.brain.domain.model.TodoItem;

/**
 * 왜: 구글 API 연동을 추상화하여 도메인이 외부 SDK 세부 구현에 의존하지 않도록 하기 위함.
 */
public interface GooglePort {
    /**
     * 왜: 캘린더 이벤트를 외부 시스템에 기록해 일정 공유를 가능하게 하기 위함.
     */
    void createCalendarEvent(CalendarEvent event);

    /**
     * 왜: 할 일 정보를 외부 시스템에 기록해 알림 및 추적을 가능하게 하기 위함.
     */
    void createTask(TodoItem todoItem, String rawMessage);

    /**
     * 왜: 최초 1회 인증 링크를 생성해 사용자가 직접 승인하도록 안내하기 위함.
     */
    String generateAuthUrl();

    /**
     * 왜: 사용자가 승인 후 받은 인증 코드를 교환하여 자격 증명을 영구 저장하기 위함.
     */
    void exchangeAuthCode(String authCode);

    /**
     * 왜: 자격 증명 존재 여부를 사전에 확인해 동기화 실패를 예방하기 위함.
     */
    boolean hasCredential();
}
