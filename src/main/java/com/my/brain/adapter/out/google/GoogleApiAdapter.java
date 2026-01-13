package com.my.brain.adapter.out.google;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.tasks.Tasks;
import com.google.api.services.tasks.model.Task;
import com.my.brain.config.AppConfig;
import com.my.brain.domain.model.CalendarEvent;
import com.my.brain.domain.model.TodoItem;
import com.my.brain.domain.port.out.GooglePort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.Retry;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

/**
 * 왜: 구글 Calendar/Tasks 연동을 도메인 포트 계약에 맞게 구현하여 시간대/링크 정책을 일관되게 적용하기 위함.
 */
@ApplicationScoped
public class GoogleApiAdapter implements GooglePort {

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter RFC3339 = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final Calendar calendar;
    private final Tasks tasks;

    @Inject
    public GoogleApiAdapter(AppConfig appConfig) {
        try {
            var httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            var jsonFactory = JacksonFactory.getDefaultInstance();
            String credentialPath = appConfig.google().credentialPath();
            var credential = com.google.api.client.googleapis.auth.oauth2.GoogleCredential
                    .fromStream(new File(credentialPath + "/credential.json").toURI().toURL().openStream())
                    .createScoped(Collections.singletonList("https://www.googleapis.com/auth/calendar https://www.googleapis.com/auth/tasks"));

            this.calendar = new Calendar.Builder(httpTransport, jsonFactory, credential)
                    .setApplicationName("my-second-brain-worker")
                    .build();
            this.tasks = new Tasks.Builder(httpTransport, jsonFactory, credential)
                    .setApplicationName("my-second-brain-worker")
                    .build();
        } catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException("구글 클라이언트 초기화 실패", e);
        }
    }

    @Override
    @Retry(maxRetries = 3, delay = 1000)
    public void createCalendarEvent(CalendarEvent event) {
        try {
            Event calendarEvent = new Event()
                    .setSummary(event.summary())
                    .setLocation(event.location())
                    .setDescription(event.description());
            calendarEvent.setStart(new EventDateTime().setDateTime(toRfc(event.timeRange().start())));
            calendarEvent.setEnd(new EventDateTime().setDateTime(toRfc(event.timeRange().end())));
            calendar.events().insert("primary", calendarEvent).execute();
        } catch (IOException e) {
            throw new RuntimeException("캘린더 이벤트 생성 실패", e);
        }
    }

    @Override
    @Retry(maxRetries = 3, delay = 1000)
    public void createTask(TodoItem todoItem, String rawMessage) {
        try {
            Task task = new Task()
                    .setTitle(todoItem.description())
                    .setNotes(rawMessage);
            if (todoItem.dueDate() != null) {
                task.setDue(toRfc(todoItem.dueDate()).toStringRfc3339());
            }
            tasks.tasks().insert("@default", task).execute();
        } catch (IOException e) {
            throw new RuntimeException("할 일 생성 실패", e);
        }
    }

    private com.google.api.client.util.DateTime toRfc(java.time.OffsetDateTime dt) {
        if (dt == null) return null;
        return new com.google.api.client.util.DateTime(dt.atZoneSameInstant(ZONE_ID).format(RFC3339));
    }
}
