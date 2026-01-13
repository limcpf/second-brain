package com.my.brain.domain.port.out;

import com.my.brain.domain.model.Note;
import com.my.brain.domain.model.BrainRequest;

/**
 * 왜: 파일 시스템 작업을 추상화하여 도메인이 저장소나 경로 구조에 종속되지 않도록 하기 위함.
 */
public interface FilePort {
    Note ensureDailyNote(BrainRequest request);
    Note appendQuickLog(Note dailyNote, String logLine);
    Note createMeetingNote(Note dailyNote, String summary, String contentMarkdown);
    void linkMeetingNote(Note dailyNote, Note meetingNote);
}
