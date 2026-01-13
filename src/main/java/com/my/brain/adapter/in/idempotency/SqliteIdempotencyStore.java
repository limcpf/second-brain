package com.my.brain.adapter.in.idempotency;

import com.my.brain.config.AppConfig;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;

/**
 * 왜: 로컬/단일 프로세스에서도 재시작 후 중복 처리를 방지하기 위해 파일 기반 SQLite를 사용한다.
 */
@IfBuildProperty(name = "app.idempotency.backend", stringValue = "sqlite")
@ApplicationScoped
public class SqliteIdempotencyStore implements IdempotencyStore {

    private static final String TABLE_DDL = """
            CREATE TABLE IF NOT EXISTS idempotency_log (
                event_id TEXT PRIMARY KEY,
                processed_at INTEGER NOT NULL
            )
            """;

    private static final String INSERT_SQL = "INSERT OR IGNORE INTO idempotency_log(event_id, processed_at) VALUES (?, ?)";
    private static final String SELECT_SQL = "SELECT 1 FROM idempotency_log WHERE event_id = ? AND processed_at >= ?";
    private static final String CLEANUP_SQL = "DELETE FROM idempotency_log WHERE processed_at < ?";
    private static final String ENABLE_WAL = "PRAGMA journal_mode=WAL";

    private final DataSource dataSource;
    private final Duration ttl;
    private final Path sqlitePath;

    public SqliteIdempotencyStore(DataSource dataSource, AppConfig appConfig) {
        this.dataSource = dataSource;
        this.ttl = Duration.ofHours(appConfig.idempotency().ttlHours());
        this.sqlitePath = Path.of(appConfig.idempotency().sqlitePath());
    }

    @PostConstruct
    void init() {
        try {
            Path parent = sqlitePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (Exception e) {
            throw new IllegalStateException("SQLite 경로 생성 실패", e);
        }
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(ENABLE_WAL);
            stmt.execute(TABLE_DDL);
        } catch (SQLException e) {
            throw new IllegalStateException("Idempotency 테이블 초기화 실패", e);
        }
    }

    @Override
    public boolean isProcessed(String eventId) {
        cleanup();
        Instant cutoff = Instant.now().minus(ttl);
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(SELECT_SQL)) {
            ps.setString(1, eventId);
            ps.setLong(2, cutoff.toEpochMilli());
            return ps.executeQuery().next();
        } catch (SQLException e) {
            throw new IllegalStateException("Idempotency 조회 실패", e);
        }
    }

    @Override
    public void markProcessed(String eventId) {
        cleanup();
        long now = Instant.now().toEpochMilli();
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
            ps.setString(1, eventId);
            ps.setLong(2, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Idempotency 기록 실패", e);
        }
    }

    private void cleanup() {
        Instant cutoff = Instant.now().minus(ttl);
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(CLEANUP_SQL)) {
            ps.setLong(1, cutoff.toEpochMilli());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Idempotency 정리 실패", e);
        }
    }
}
