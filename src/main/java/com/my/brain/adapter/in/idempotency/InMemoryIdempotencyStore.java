package com.my.brain.adapter.in.idempotency;

import com.my.brain.config.AppConfig;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@IfBuildProperty(name = "app.idempotency.backend", stringValue = "memory")
@ApplicationScoped
public class InMemoryIdempotencyStore implements IdempotencyStore {

    private final Duration ttl;
    private final Map<String, Instant> processed = new ConcurrentHashMap<>();

    public InMemoryIdempotencyStore(AppConfig appConfig) {
        this.ttl = Duration.ofHours(appConfig.idempotency().ttlHours());
    }

    @Override
    public boolean isProcessed(String eventId) {
        cleanup();
        return processed.containsKey(eventId);
    }

    @Override
    public void markProcessed(String eventId) {
        cleanup();
        processed.put(eventId, Instant.now());
    }

    private void cleanup() {
        Instant cutoff = Instant.now().minus(ttl);
        processed.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }
}
