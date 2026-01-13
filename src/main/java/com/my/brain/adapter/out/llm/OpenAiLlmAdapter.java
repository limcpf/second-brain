package com.my.brain.adapter.out.llm;

import com.my.brain.config.AppConfig;
import com.my.brain.domain.exception.IntentParseException;
import com.my.brain.domain.model.BrainRequest;
import com.my.brain.domain.model.CalendarEvent;
import com.my.brain.domain.model.IntentType;
import com.my.brain.domain.model.LlmIntentResult;
import com.my.brain.domain.model.Note;
import com.my.brain.domain.model.TodoItem;
import com.my.brain.domain.port.out.LlmPort;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.Retry;

import java.util.Map;

/**
 * 왜: LLM 호출을 도메인 포트 계약에 맞게 감싸 의도 분류와 구조화 결과를 안정적으로 제공하기 위함.
 */
@ApplicationScoped
public class OpenAiLlmAdapter implements LlmPort {

    private final IntentParser intentParser;

    @Inject
    public OpenAiLlmAdapter(AppConfig appConfig) {
        String apiKey = appConfig.openai().apiKey().orElse("");
        OpenAiChatModel model = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(appConfig.openai().model())
                .temperature(appConfig.openai().temperature())
                .build();
        this.intentParser = AiServices.builder(IntentParser.class)
                .chatLanguageModel(model)
                .build();
    }

    @Override
    @Retry(maxRetries = 3, delay = 1000)
    public LlmIntentResult parseIntent(BrainRequest request) {
        IntentResponse body = intentParser.parse(
                request.content(),
                Map.of(
                        "eventId", request.eventId(),
                        "userId", request.userId(),
                        "timestamp", request.timestamp().toString()
                )
        );
        if (body == null || body.intent() == null) {
            throw new IntentParseException("LLM Intent 응답이 비어 있습니다.");
        }
        IntentType intentType = IntentType.valueOf(body.intent());
        return new LlmIntentResult(
                intentType,
                body.calendarEvent(),
                body.todoItem(),
                body.note()
        );
    }

    interface IntentParser {
        @SystemMessage("현재 시각: {{timestamp}}. 사용자의 명령을 CALENDAR, TASK, NOTE, SYNC, UNKNOWN 중 하나의 intent로 분류하고 필요한 필드를 JSON으로 반환하세요.")
        IntentResponse parse(String userMessage, Map<String, Object> context);
    }

    public record IntentResponse(String intent, CalendarEvent calendarEvent, TodoItem todoItem, Note note) {}
}
