package com.my.brain.adapter.out.google;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.tasks.Tasks;
import com.google.api.services.tasks.model.Task;
import com.my.brain.config.AppConfig;
import com.my.brain.domain.exception.GoogleCredentialNotFoundException;
import com.my.brain.domain.model.CalendarEvent;
import com.my.brain.domain.model.TodoItem;
import com.my.brain.domain.port.out.GooglePort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.Retry;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 왜: 구글 Calendar/Tasks 연동을 도메인 포트 계약에 맞게 구현하여 시간대/링크 정책을 일관되게 적용하기 위함.
 */
@ApplicationScoped
public class GoogleApiAdapter implements GooglePort {

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter RFC3339 = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final List<String> SCOPES = List.of(
            "https://www.googleapis.com/auth/calendar",
            "https://www.googleapis.com/auth/tasks"
    );
    private static final String REDIRECT_URI = "urn:ietf:wg:oauth:2.0:oob";
    private static final String TOKEN_USER_ID = "default";
    private static final String APP_NAME = "my-second-brain-worker";

    private final Path credentialDir;
    private final Path clientSecretPath;
    private final JacksonFactory jsonFactory;
    private final NetHttpTransport httpTransport;

    @Inject
    public GoogleApiAdapter(AppConfig appConfig) {
        try {
            this.credentialDir = Path.of(appConfig.google().credentialPath());
            Files.createDirectories(credentialDir);
            this.clientSecretPath = credentialDir.resolve("client_secret.json");
            this.httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            this.jsonFactory = JacksonFactory.getDefaultInstance();
        } catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException("구글 클라이언트 초기화 실패", e);
        }
    }

    @Override
    @Retry(maxRetries = 3, delay = 1000)
    public void createCalendarEvent(CalendarEvent event) {
        try {
            Credential credential = loadCredentialOrThrow();
            Calendar calendar = new Calendar.Builder(httpTransport, jsonFactory, credential)
                    .setApplicationName(APP_NAME)
                    .build();
            Event calendarEvent = new Event()
                    .setSummary(event.summary())
                    .setLocation(event.location())
                    .setDescription(event.description());
            calendarEvent.setStart(new EventDateTime().setDateTime(toRfc(event.timeRange().start())));
            calendarEvent.setEnd(new EventDateTime().setDateTime(toRfc(event.timeRange().end())));
            calendar.events().insert("primary", calendarEvent).execute();
        } catch (GoogleCredentialNotFoundException e) {
            throw e;
        } catch (IOException e) {
            throw new RuntimeException("캘린더 이벤트 생성 실패", e);
        }
    }

    @Override
    @Retry(maxRetries = 3, delay = 1000)
    public void createTask(TodoItem todoItem, String rawMessage) {
        try {
            Credential credential = loadCredentialOrThrow();
            Tasks tasks = new Tasks.Builder(httpTransport, jsonFactory, credential)
                    .setApplicationName(APP_NAME)
                    .build();
            Task task = new Task()
                    .setTitle(todoItem.description())
                    .setNotes(rawMessage);
            if (todoItem.dueDate() != null) {
                task.setDue(toRfc(todoItem.dueDate()).toStringRfc3339());
            }
            tasks.tasks().insert("@default", task).execute();
        } catch (GoogleCredentialNotFoundException e) {
            throw e;
        } catch (IOException e) {
            throw new RuntimeException("할 일 생성 실패", e);
        }
    }

    @Override
    public String generateAuthUrl() {
        GoogleAuthorizationCodeFlow flow = buildFlow();
        return flow.newAuthorizationUrl()
                .setRedirectUri(REDIRECT_URI)
                .build();
    }

    @Override
    public void exchangeAuthCode(String authCode) {
        GoogleAuthorizationCodeFlow flow = buildFlow();
        try {
            flow.createAndStoreCredential(
                    flow.newTokenRequest(authCode).setRedirectUri(REDIRECT_URI).execute(),
                    TOKEN_USER_ID
            );
        } catch (IOException e) {
            throw new RuntimeException("구글 인증 코드 교환 실패", e);
        }
    }

    @Override
    public boolean hasCredential() {
        if (!Files.exists(clientSecretPath)) {
            return false;
        }
        GoogleAuthorizationCodeFlow flow = buildFlow();
        try {
            Credential credential = flow.loadCredential(TOKEN_USER_ID);
            return credential != null && credential.getRefreshToken() != null;
        } catch (IOException e) {
            return false;
        }
    }

    private Credential loadCredentialOrThrow() {
        GoogleAuthorizationCodeFlow flow = buildFlow();
        try {
            Credential credential = flow.loadCredential(TOKEN_USER_ID);
            if (credential == null) {
                throw new GoogleCredentialNotFoundException(buildAuthUrl(flow));
            }
            return credential;
        } catch (IOException e) {
            throw new RuntimeException("구글 자격 증명 로드 실패", e);
        }
    }

    private String buildAuthUrl(GoogleAuthorizationCodeFlow flow) {
        return flow.newAuthorizationUrl()
                .setRedirectUri(REDIRECT_URI)
                .build();
    }

    private GoogleAuthorizationCodeFlow buildFlow() {
        if (!Files.exists(clientSecretPath)) {
            throw new RuntimeException("client_secret.json 파일이 필요합니다: " + clientSecretPath);
        }
        try (Reader reader = Files.newBufferedReader(clientSecretPath)) {
            GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(jsonFactory, reader);
            return new GoogleAuthorizationCodeFlow.Builder(httpTransport, jsonFactory, clientSecrets, SCOPES)
                    .setDataStoreFactory(new FileDataStoreFactory(credentialDir.toFile()))
                    .setAccessType("offline")
                    .setApprovalPrompt("force")
                    .build();
        } catch (IOException e) {
            throw new RuntimeException("구글 OAuth 플로우 초기화 실패", e);
        }
    }

    private com.google.api.client.util.DateTime toRfc(java.time.OffsetDateTime dt) {
        if (dt == null) return null;
        return new com.google.api.client.util.DateTime(dt.atZoneSameInstant(ZONE_ID).format(RFC3339));
    }
}
