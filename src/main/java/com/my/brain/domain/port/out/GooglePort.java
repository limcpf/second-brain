package com.my.brain.domain.port.out;

import com.my.brain.domain.model.CalendarEvent;
import com.my.brain.domain.model.TodoItem;

/**
 * 왜: 구글 API 연동을 추상화하여 도메인이 외부 SDK 세부 구현에 의존하지 않도록 하기 위함.
 */
public interface GooglePort {
    void createCalendarEvent(CalendarEvent event);
    void createTask(TodoItem todoItem, String rawMessage);
}
