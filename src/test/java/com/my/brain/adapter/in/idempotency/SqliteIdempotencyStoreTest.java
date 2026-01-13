package com.my.brain.adapter.in.idempotency;

import com.my.brain.config.AppConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SqliteIdempotencyStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void storesAndCleansWithTtl() throws Exception {
        Path dbPath = tempDir.resolve("idempotency.db");
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + dbPath.toAbsolutePath());

        SqliteIdempotencyStore store = new SqliteIdempotencyStore(dataSource, new TestConfig(dbPath));
        store.init();

        assertThat(store.isProcessed("evt-1")).isFalse();

        store.markProcessed("evt-1");
        assertThat(store.isProcessed("evt-1")).isTrue();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE idempotency_log SET processed_at = ? WHERE event_id = ?")) {
            long past = Instant.now().minusSeconds(4000).toEpochMilli();
            ps.setLong(1, past);
            ps.setString(2, "evt-1");
            ps.executeUpdate();
        }

        assertThat(store.isProcessed("evt-1")).isFalse();
    }

    private static class TestConfig implements AppConfig {

        private final Path sqlitePath;

        private TestConfig(Path sqlitePath) {
            this.sqlitePath = sqlitePath;
        }

        @Override
        public OpenAiConfig openai() {
            return new OpenAiConfig() {
                @Override
                public Optional<String> apiKey() {
                    return Optional.empty();
                }

                @Override
                public String model() {
                    return "gpt-5-mini";
                }

                @Override
                public double temperature() {
                    return 0.2;
                }
            };
        }

        @Override
        public GoogleConfig google() {
            return () -> "./config/tokens";
        }

        @Override
        public PathsConfig paths() {
            return new PathsConfig() {
                @Override
                public String vaultPath() {
                    return "./data/vault";
                }

                @Override
                public String templatePath() {
                    return "src/main/resources/templates";
                }
            };
        }

        @Override
        public IdempotencyConfig idempotency() {
            return new IdempotencyConfig() {
                @Override
                public String backend() {
                    return "sqlite";
                }

                @Override
                public String path() {
                    return "./data/idempotency.log";
                }

                @Override
                public String sqlitePath() {
                    return sqlitePath.toString();
                }

                @Override
                public int ttlHours() {
                    return 1;
                }
            };
        }

        @Override
        public DockerConfig docker() {
            return new DockerConfig() {
                @Override
                public String host() {
                    return "unix:///var/run/docker.sock";
                }

                @Override
                public String image() {
                    return "obsidian-livesync-client:latest";
                }

                @Override
                public int syncWaitSeconds() {
                    return 180;
                }
            };
        }
    }
}
