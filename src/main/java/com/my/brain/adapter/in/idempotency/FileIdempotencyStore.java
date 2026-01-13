package com.my.brain.adapter.in.idempotency;

import com.my.brain.config.AppConfig;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

@IfBuildProfile("prod")
@ApplicationScoped
public class FileIdempotencyStore implements IdempotencyStore {

    private final Path logPath;
    private final Duration ttl;

    public FileIdempotencyStore(AppConfig appConfig) {
        this.logPath = Path.of(appConfig.idempotency().path());
        this.ttl = Duration.ofHours(appConfig.idempotency().ttlHours());
        try {
            Path parent = logPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (!Files.exists(logPath)) {
                Files.createFile(logPath);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Idempotency 로그 파일 초기화 실패", e);
        }
    }

    @Override
    public boolean isProcessed(String eventId) {
        cleanup();
        try (Stream<String> lines = Files.lines(logPath)) {
            return lines.anyMatch(line -> line.startsWith(eventId + "|"));
        } catch (IOException e) {
            throw new IllegalStateException("Idempotency 로그 조회 실패", e);
        }
    }

    @Override
    public void markProcessed(String eventId) {
        cleanup();
        String entry = eventId + "|" + Instant.now().toEpochMilli() + System.lineSeparator();
        try {
            Files.writeString(logPath, entry, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException("Idempotency 로그 기록 실패", e);
        }
    }

    private void cleanup() {
        Instant cutoff = Instant.now().minus(ttl);
        try {
            if (!Files.exists(logPath)) {
                return;
            }
            var retained = Files.readAllLines(logPath).stream()
                    .filter(line -> isFresh(line, cutoff))
                    .toList();
            Files.write(logPath, retained);
        } catch (IOException e) {
            throw new IllegalStateException("Idempotency 로그 정리 실패", e);
        }
    }

    private boolean isFresh(String line, Instant cutoff) {
        int separatorIndex = line.indexOf('|');
        if (separatorIndex < 0) {
            return true;
        }
        try {
            long timestamp = Long.parseLong(line.substring(separatorIndex + 1).trim());
            return Instant.ofEpochMilli(timestamp).isAfter(cutoff);
        } catch (NumberFormatException e) {
            return true;
        }
    }
}
