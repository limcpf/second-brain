package com.my.brain.config;

import io.quarkus.runtime.annotations.StaticInitSafe;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.util.Optional;

@StaticInitSafe
@ConfigMapping(prefix = "app")
public interface AppConfig {

    OpenAiConfig openai();

    GoogleConfig google();

    PathsConfig paths();

    IdempotencyConfig idempotency();

    DockerConfig docker();

    TelegramConfig telegram();

    interface OpenAiConfig {
        @WithName("api-key")
        Optional<String> apiKey();

        @WithDefault("gpt-5-mini")
        String model();

        @WithDefault("0.2")
        double temperature();
    }

    interface GoogleConfig {
        @WithName("credential-path")
        @WithDefault("/app/config/tokens")
        String credentialPath();
    }

    interface PathsConfig {
        @WithName("vault-path")
        @WithDefault("./data/vault")
        String vaultPath();

        @WithName("template-path")
        @WithDefault("src/main/resources/templates")
        String templatePath();
    }

    interface IdempotencyConfig {
        @WithName("backend")
        @WithDefault("sqlite")
        String backend();

        @WithName("path")
        @WithDefault("./data/idempotency.log")
        String path();

        @WithName("sqlite-path")
        @WithDefault("./data/idempotency.db")
        String sqlitePath();

        @WithName("ttl-hours")
        @WithDefault("24")
        int ttlHours();
    }

    interface DockerConfig {
        @WithName("host")
        @WithDefault("unix:///var/run/docker.sock")
        String host();

        @WithName("image")
        @WithDefault("lscr.io/linuxserver/obsidian:latest")
        String image();

        @WithName("sync-wait-seconds")
        @WithDefault("30")
        int syncWaitSeconds();

        @WithName("config-path")
        @WithDefault("./data/obsidian-config")
        String configPath();

        @WithName("puid")
        @WithDefault("1000")
        String puid();

        @WithName("pgid")
        @WithDefault("1000")
        String pgid();

        @WithName("timezone")
        @WithDefault("Etc/UTC")
        String timezone();
    }

    interface TelegramConfig {
        @WithName("bot-token")
        Optional<String> botToken();

        @WithName("poll-interval-seconds")
        @WithDefault("5")
        int pollIntervalSeconds();

        @WithName("poll-timeout-seconds")
        @WithDefault("30")
        int pollTimeoutSeconds();
    }
}
