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
        @WithName("path")
        @WithDefault("./data/idempotency.log")
        String path();

        @WithName("ttl-hours")
        @WithDefault("24")
        int ttlHours();
    }

    interface DockerConfig {
        @WithName("host")
        @WithDefault("unix:///var/run/docker.sock")
        String host();

        @WithName("image")
        @WithDefault("obsidian-livesync-client:latest")
        String image();

        @WithName("sync-wait-seconds")
        @WithDefault("180")
        int syncWaitSeconds();
    }
}
