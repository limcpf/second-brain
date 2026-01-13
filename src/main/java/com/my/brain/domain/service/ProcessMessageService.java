package com.my.brain.domain.service;

import com.my.brain.domain.exception.IntentParseException;
import com.my.brain.domain.model.BrainRequest;
import com.my.brain.domain.model.IntentType;
import com.my.brain.domain.model.LlmIntentResult;
import com.my.brain.domain.model.Note;
import com.my.brain.domain.model.ReplyMessage;
import com.my.brain.domain.port.in.ProcessMessageUseCase;
import com.my.brain.domain.port.out.ClockPort;
import com.my.brain.domain.port.out.DockerPort;
import com.my.brain.domain.port.out.FilePort;
import com.my.brain.domain.port.out.GooglePort;
import com.my.brain.domain.port.out.LlmPort;
import com.my.brain.domain.port.out.ReplyPort;

/**
 * 왜: 모든 입력 처리를 단일 유스케이스로 수렴시켜 의도 파악, 외부 연동, 응답 전송을 일관되게 연결하기 위함.
 */
public class ProcessMessageService implements ProcessMessageUseCase {

    private final LlmPort llmPort;
    private final FilePort filePort;
    private final GooglePort googlePort;
    private final DockerPort dockerPort;
    private final ReplyPort replyPort;
    private final ClockPort clockPort;

    public ProcessMessageService(LlmPort llmPort,
                                 FilePort filePort,
                                 GooglePort googlePort,
                                 DockerPort dockerPort,
                                 ReplyPort replyPort,
                                 ClockPort clockPort) {
        this.llmPort = llmPort;
        this.filePort = filePort;
        this.googlePort = googlePort;
        this.dockerPort = dockerPort;
        this.replyPort = replyPort;
        this.clockPort = clockPort;
    }

    @Override
    public ReplyMessage process(BrainRequest request) {
        // LLM으로 의도 파싱
        LlmIntentResult intentResult = llmPort.parseIntent(request);
        if (intentResult == null) {
            throw new IntentParseException("LLM 결과가 null입니다.");
        }

        return switch (intentResult.intentType()) {
            case CALENDAR -> handleCalendar(request, intentResult);
            case TASK -> handleTask(request, intentResult);
            case NOTE -> handleNote(request, intentResult);
            case SYNC -> handleSync(request);
            case UNKNOWN -> buildReply(request.userId(), "❓ 요청을 이해하지 못했습니다. 다시 시도해주세요.");
        };
    }

    private ReplyMessage handleCalendar(BrainRequest request, LlmIntentResult intentResult) {
        if (intentResult.calendarEvent() == null) {
            throw new IntentParseException("캘린더 이벤트 정보가 없습니다.");
        }
        // 노트 선 생성 후 링크 삽입을 위해 파일 시스템 작업
        Note meetingNote = filePort.createMeetingNote(
                filePort.ensureDailyNote(request),
                intentResult.calendarEvent().summary(),
                intentResult.calendarEvent().description()
        );
        filePort.linkMeetingNote(filePort.ensureDailyNote(request), meetingNote);

        // 구글 캘린더 등록 시 노트 링크 삽입
        googlePort.createCalendarEvent(intentResult.calendarEvent());
        ReplyMessage reply = buildReply(request.userId(), "✅ 일정이 등록되었습니다. (관련 노트: " + meetingNote.title() + ")");
        replyPort.send(reply);
        return reply;
    }

    private ReplyMessage handleTask(BrainRequest request, LlmIntentResult intentResult) {
        if (intentResult.todoItem() == null) {
            throw new IntentParseException("할 일 정보가 없습니다.");
        }
        googlePort.createTask(intentResult.todoItem(), request.content());
        ReplyMessage reply = buildReply(request.userId(), "✅ 할 일이 등록되었습니다.");
        replyPort.send(reply);
        return reply;
    }

    private ReplyMessage handleNote(BrainRequest request, LlmIntentResult intentResult) {
        Note dailyNote = filePort.ensureDailyNote(request);
        filePort.appendQuickLog(dailyNote, clockPort.now().toLocalTime() + " - " + request.content());
        ReplyMessage reply = buildReply(request.userId(), "📝 노트에 기록했습니다.");
        replyPort.send(reply);
        return reply;
    }

    private ReplyMessage handleSync(BrainRequest request) {
        String id = dockerPort.runSyncContainer();
        // 도메인은 동기화 완료 여부를 직접 기다리지 않고 성공 메시지를 반환
        ReplyMessage reply = buildReply(request.userId(), "🔄 동기화를 시작했습니다. (컨테이너: " + id + ")");
        replyPort.send(reply);
        return reply;
    }

    private ReplyMessage buildReply(String userId, String content) {
        return new ReplyMessage(userId, content);
    }
}
