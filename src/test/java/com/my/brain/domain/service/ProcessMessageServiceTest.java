package com.my.brain.domain.service;

import com.my.brain.domain.exception.IntentParseException;
import com.my.brain.domain.model.*;
import com.my.brain.domain.port.out.ClockPort;
import com.my.brain.domain.port.out.DockerPort;
import com.my.brain.domain.port.out.FilePort;
import com.my.brain.domain.port.out.GooglePort;
import com.my.brain.domain.port.out.LlmPort;
import com.my.brain.domain.port.out.ReplyPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProcessMessageServiceTest {

    private LlmPort llmPort;
    private FilePort filePort;
    private GooglePort googlePort;
    private DockerPort dockerPort;
    private ReplyPort replyPort;
    private ClockPort clockPort;
    private ProcessMessageService service;

    @BeforeEach
    void setUp() {
        llmPort = mock(LlmPort.class);
        filePort = mock(FilePort.class);
        googlePort = mock(GooglePort.class);
        dockerPort = mock(DockerPort.class);
        replyPort = mock(ReplyPort.class);
        clockPort = mock(ClockPort.class);
        service = new ProcessMessageService(llmPort, filePort, googlePort, dockerPort, replyPort, clockPort);
    }

    @Test
    void calendar_intent_creates_event_and_reply() {
        BrainRequest req = new BrainRequest("id", OffsetDateTime.now(), "user", MessageType.CHAT, "content");
        CalendarEvent event = new CalendarEvent("summary", new TimeRange(OffsetDateTime.now(), OffsetDateTime.now().plusHours(1)), "loc", null, "desc", new Note("p", "t", "c"));
        when(llmPort.parseIntent(req)).thenReturn(new LlmIntentResult(IntentType.CALENDAR, event, null, null));
        when(filePort.ensureDailyNote(req)).thenReturn(new Note("2026-01-01.md", "2026-01-01", ""));
        when(filePort.createMeetingNote(any(), any(), any())).thenReturn(new Note("Meeting-Notes/2026-01-01-sum.md", "sum", ""));

        ReplyMessage reply = service.process(req);

        verify(googlePort).createCalendarEvent(event);
        verify(replyPort).send(reply);
        assertTrue(reply.content().contains("✅"));
    }

    @Test
    void task_intent_creates_task_and_reply() {
        BrainRequest req = new BrainRequest("id", OffsetDateTime.now(), "user", MessageType.CHAT, "content");
        TodoItem todo = new TodoItem("할 일", null);
        when(llmPort.parseIntent(req)).thenReturn(new LlmIntentResult(IntentType.TASK, null, todo, null));

        ReplyMessage reply = service.process(req);

        verify(googlePort).createTask(todo, req.content());
        verify(replyPort).send(reply);
    }

    @Test
    void sync_intent_runs_docker() {
        BrainRequest req = new BrainRequest("id", OffsetDateTime.now(), "user", MessageType.SYNC, "content");
        when(llmPort.parseIntent(req)).thenReturn(new LlmIntentResult(IntentType.SYNC, null, null, null));
        when(dockerPort.runSyncContainer()).thenReturn("cid");

        ReplyMessage reply = service.process(req);

        verify(dockerPort).runSyncContainer();
        verify(replyPort).send(reply);
        assertTrue(reply.content().contains("🔄"));
    }

    @Test
    void null_intent_throws() {
        BrainRequest req = new BrainRequest("id", OffsetDateTime.now(), "user", MessageType.CHAT, "content");
        when(llmPort.parseIntent(req)).thenReturn(null);
        assertThrows(IntentParseException.class, () -> service.process(req));
    }
}
