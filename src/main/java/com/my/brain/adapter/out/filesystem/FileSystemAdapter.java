package com.my.brain.adapter.out.filesystem;

import com.my.brain.domain.model.BrainRequest;
import com.my.brain.domain.model.Note;
import com.my.brain.domain.port.out.FilePort;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * 왜: Obsidian vault 파일 생성/갱신을 중앙집중식으로 처리하여 링크 일관성과 템플릿 적용을 보장하기 위함.
 */
@ApplicationScoped
public class FileSystemAdapter implements FilePort {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final Path vaultRoot;
    private final Path templateRoot;

    public FileSystemAdapter() {
        this.vaultRoot = Path.of(System.getenv().getOrDefault("VAULT_PATH", "./data/vault"));
        this.templateRoot = Path.of(System.getenv().getOrDefault("TEMPLATE_PATH", "src/main/resources/templates"));
    }

    @Override
    public Note ensureDailyNote(BrainRequest request) {
        LocalDate date = request.timestamp().atZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDate();
        String filename = DATE.format(date) + ".md";
        Path dailyPath = vaultRoot.resolve(filename);
        try {
            if (!Files.exists(dailyPath)) {
                String template = loadTemplate("daily.md");
                String content = template.replace("{{date}}", DATE.format(date));
                Files.createDirectories(dailyPath.getParent());
                Files.writeString(dailyPath, content, StandardCharsets.UTF_8);
            }
            return new Note(relativize(dailyPath), filename, Files.readString(dailyPath));
        } catch (IOException e) {
            throw new RuntimeException("데일리 노트 생성/로딩 실패", e);
        }
    }

    @Override
    public Note appendQuickLog(Note dailyNote, String logLine) {
        Objects.requireNonNull(dailyNote);
        Path path = vaultRoot.resolve(dailyNote.path());
        try {
            StringBuilder updated = new StringBuilder(Files.readString(path));
            if (!updated.toString().endsWith("\n")) {
                updated.append("\n");
            }
            updated.append("## Logs\n").append("- ").append(logLine).append("\n");
            Files.writeString(path, updated.toString(), StandardCharsets.UTF_8);
            return new Note(dailyNote.path(), dailyNote.title(), updated.toString());
        } catch (IOException e) {
            throw new RuntimeException("퀵 로그 추가 실패", e);
        }
    }

    @Override
    public Note createMeetingNote(Note dailyNote, String summary, String contentMarkdown) {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Seoul"));
        String fileName = "Meeting-Notes/" + DATE.format(date) + "-" + summary + ".md";
        Path path = vaultRoot.resolve(fileName);
        try {
            Files.createDirectories(path.getParent());
            String body = "# " + summary + "\n\n" + (contentMarkdown == null ? "" : contentMarkdown);
            Files.writeString(path, body, StandardCharsets.UTF_8);
            return new Note(relativize(path), summary, body);
        } catch (IOException e) {
            throw new RuntimeException("미팅 노트 생성 실패", e);
        }
    }

    @Override
    public void linkMeetingNote(Note dailyNote, Note meetingNote) {
        Path dailyPath = vaultRoot.resolve(dailyNote.path());
        try {
            String content = Files.readString(dailyPath);
            String link = "[[" + meetingNote.path() + "]]";
            if (!content.contains(link)) {
                content += "\n- 관련 미팅: " + link + "\n";
                Files.writeString(dailyPath, content, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("미팅 노트 링크 삽입 실패", e);
        }
    }

    private String loadTemplate(String name) throws IOException {
        Path path = templateRoot.resolve(name);
        if (!Files.exists(path)) {
            return "---\ntitle: {{date}}\n---\n\n## Tasks\n\n## Logs\n";
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private String relativize(Path path) {
        return vaultRoot.relativize(path).toString().replace('\\', '/');
    }
}
