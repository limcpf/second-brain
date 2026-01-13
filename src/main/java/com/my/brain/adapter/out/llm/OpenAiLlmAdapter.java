package com.my.brain.adapter.out.llm;

import com.my.brain.domain.exception.IntentParseException;
import com.my.brain.domain.model.BrainRequest;
import com.my.brain.domain.model.CalendarEvent;
import com.my.brain.domain.model.IntentType;
import com.my.brain.domain.model.LlmIntentResult;
import com.my.brain.domain.model.Note;
import com.my.brain.domain.model.TimeRange;
import com.my.brain.domain.model.TodoItem;
import com.my.brain.domain.port.out.LlmPort;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 왜: LLM 호출을 도메인 포트 계약에 맞게 감싸 의도 분류와 구조화 결과를 안정적으로 제공하기 위함.
 */
@ApplicationScoped
public class OpenAiLlmAdapter implements LlmPort {

    private final IntentParser intentParser;

    public OpenAiLlmAdapter() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        OpenAiChatModel model = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName("gpt-5-mini")
                .temperature(0.2)
                .build();
        this.intentParser = AiServices.builder(IntentParser.class)
                .chatLanguageModel(model)
                .build();
    }

    @Override
    public LlmIntentResult parseIntent(BrainRequest request) {
        Response<IntentResponse> response = intentParser.parse(
                request.content(),
                Map.of(
                        "eventId", request.eventId(),
                        "userId", request.userId(),
                        "timestamp", request.timestamp().toString()
                )
        );
        IntentResponse body = response.content();
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

    // 단순 변환용 팩토리 (LLM 출력 스키마를 유연하게 받을 수 있게 예시만 포함)
    private static CalendarEvent toCalendarEvent(String summary, String start, String end, String location, String description) {
        OffsetDateTime s = start == null ? null : OffsetDateTime.parse(start);
        OffsetDateTime e = end == null ? null : OffsetDateTime.parse(end);
        return new CalendarEvent(summary, new TimeRange(s, e), location, null, description, null);
    }

    private static TodoItem toTodo(String description, String due) {
        OffsetDateTime d = due == null ? null : OffsetDateTime.parse(due);
        return new TodoItem(description, d);
    }

    private static Note toNote(String title, String content) {
        return new Note(title, title, content);
    }
}
