package com.my.brain.adapter.out.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.my.brain.config.AppConfig;
import com.my.brain.domain.model.TelegramIncomingMessage;
import com.my.brain.domain.model.TelegramOutgoingMessage;
import com.my.brain.domain.port.out.TelegramSendPort;
import com.my.brain.domain.port.out.TelegramUpdatePort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 왜: 텔레그램 HTTP API 호출을 캡슐화해 도메인 포트 구현을 단순화하기 위함.
 */
@ApplicationScoped
public class TelegramBotClient implements TelegramSendPort, TelegramUpdatePort {

    private static final Logger log = Logger.getLogger(TelegramBotClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AppConfig.TelegramConfig telegramConfig;
    private final String apiBase;

    @Inject
    public TelegramBotClient(AppConfig appConfig, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.telegramConfig = appConfig.telegram();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.apiBase = telegramConfig.botToken()
                .map(token -> "https://api.telegram.org/bot" + token)
                .orElse("");
    }

    @Override
    public void send(TelegramOutgoingMessage message) {
        if (apiBase.isBlank()) {
            log.warn("텔레그램 봇 토큰이 설정되지 않아 전송을 건너뜁니다.");
            return;
        }
        try {
            String body = objectMapper.writeValueAsString(new SendMessageRequest(message.chatId(), message.text(), message.parseMode()));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBase + "/sendMessage"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.warnf("텔레그램 전송 실패 status=%d body=%s", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.warnf("텔레그램 전송 중 예외: %s", e.getMessage());
        }
    }

    @Override
    public List<TelegramIncomingMessage> fetchUpdates(long offset, int timeoutSeconds) {
        if (apiBase.isBlank()) {
            return Collections.emptyList();
        }
        try {
            String url = apiBase + "/getUpdates?timeout=" + timeoutSeconds + (offset > 0 ? "&offset=" + offset : "");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSeconds + 5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.warnf("텔레그램 업데이트 조회 실패 status=%d body=%s", response.statusCode(), response.body());
                return List.of();
            }
            TelegramApiResponse apiResponse = objectMapper.readValue(response.body(), TelegramApiResponse.class);
            if (!apiResponse.ok()) {
                log.warn("텔레그램 업데이트 응답이 ok=false 입니다.");
                return List.of();
            }
            return Optional.ofNullable(apiResponse.result())
                    .orElse(List.of())
                    .stream()
                    .map(this::mapToDomain)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();
        } catch (Exception e) {
            log.warnf("텔레그램 업데이트 조회 중 예외: %s", e.getMessage());
            return List.of();
        }
    }

    private Optional<TelegramIncomingMessage> mapToDomain(TelegramUpdate update) {
        if (update == null || update.message() == null || update.message().chat() == null) {
            return Optional.empty();
        }
        TelegramMessage message = update.message();
        if (message.text() == null) {
            return Optional.empty();
        }
        String from = Optional.ofNullable(message.from())
                .map(TelegramUser::username)
                .orElse("unknown");
        long epochSeconds = Optional.ofNullable(message.date()).orElse(0L);
        return Optional.of(new TelegramIncomingMessage(update.updateId(), message.chat().id(), from, message.text(), epochSeconds));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SendMessageRequest(@JsonProperty("chat_id") long chatId,
                                      @JsonProperty("text") String text,
                                      @JsonProperty("parse_mode") String parseMode) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TelegramApiResponse(@JsonProperty("ok") boolean ok,
                                       @JsonProperty("result") List<TelegramUpdate> result) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TelegramUpdate(@JsonProperty("update_id") long updateId,
                                  @JsonProperty("message") TelegramMessage message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TelegramMessage(@JsonProperty("message_id") long messageId,
                                   @JsonProperty("from") TelegramUser from,
                                   @JsonProperty("chat") TelegramChat chat,
                                   @JsonProperty("text") String text,
                                   @JsonProperty("date") Long date) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TelegramChat(@JsonProperty("id") long id,
                                @JsonProperty("type") String type) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TelegramUser(@JsonProperty("id") long id,
                                @JsonProperty("username") String username) {
    }
}
