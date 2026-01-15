package com.my.brain.adapter.in.idempotency;

import com.my.brain.config.AppConfig;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryIdempotencyStoreTest {

    @Test
    void marksAndDetectsProcessedEvent() {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(new TestConfig());

        assertThat(store.isProcessed("evt-1")).isFalse();

        store.markProcessed("evt-1");

        assertThat(store.isProcessed("evt-1")).isTrue();
    }

    private static class TestConfig implements AppConfig {
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
                     return "memory";
                 }

                 @Override
                 public String path() {
                     return "./data/idempotency.log";
                 }

                 @Override
                 public String sqlitePath() {
                     return "./data/idempotency.db";
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
                    return "lscr.io/linuxserver/obsidian:latest";
                }

                @Override
                public int syncWaitSeconds() {
                    return 30;
                }

                @Override
                public String configPath() {
                    return "./data/obsidian-config";
                }

                @Override
                public String puid() {
                    return "1000";
                }

                @Override
                public String pgid() {
                    return "1000";
                }

                @Override
                public String timezone() {
                    return "Etc/UTC";
                }
            };
        }

        @Override
        public TelegramConfig telegram() {
            return new TelegramConfig() {
                @Override
                public Optional<String> botToken() {
                    return Optional.empty();
                }

                @Override
                public int pollIntervalSeconds() {
                    return 1;
                }

                @Override
                public int pollTimeoutSeconds() {
                    return 1;
                }
            };
        }
    }
}
