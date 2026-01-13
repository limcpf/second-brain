package com.my.brain.adapter.out.docker;

import com.my.brain.domain.port.out.DockerPort;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 왜: 개발 환경에서 실제 Docker 제어 없이 로깅만 수행해 안전하게 동작 검증하기 위함.
 */
@IfBuildProfile("dev")
@ApplicationScoped
public class DevDockerAdapter implements DockerPort {

    private static final Logger log = Logger.getLogger(DevDockerAdapter.class);
    private final ConcurrentHashMap<String, Boolean> running = new ConcurrentHashMap<>();

    @Override
    public String runSyncContainer() {
        String id = "mock-" + System.currentTimeMillis();
        running.put(id, true);
        log.infof("[DEV MOCK] run sync container: %s", id);
        return id;
    }

    @Override
    public void stopAndCleanup(String containerId) {
        running.remove(containerId);
        log.infof("[DEV MOCK] stop/cleanup: %s", containerId);
    }

    @Override
    public boolean isRunning(String containerId) {
        return Boolean.TRUE.equals(running.get(containerId));
    }
}
