package com.my.brain.domain.port.out;

/**
 * 왜: 동기화 컨테이너 실행/정지를 외부 시스템 의존 없이 도메인 관점에서 제어하기 위함.
 */
public interface DockerPort {
    String runSyncContainer();
    void stopAndCleanup(String containerId);
    boolean isRunning(String containerId);
}
