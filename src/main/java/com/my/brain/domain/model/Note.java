package com.my.brain.domain.model;

import java.util.Objects;

/**
 * 왜: Obsidian에 기록될 노트를 구조화하여 링크/경로 생성을 일관되게 하기 위함.
 */
public record Note(String path, String title, String content) {
    public Note {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(content, "content");
        if (title.isBlank()) {
            throw new IllegalArgumentException("노트 제목은 비어 있을 수 없습니다.");
        }
    }
}
