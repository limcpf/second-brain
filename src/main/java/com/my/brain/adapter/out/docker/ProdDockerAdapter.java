package com.my.brain.adapter.out.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.my.brain.config.AppConfig;
import com.my.brain.domain.port.out.DockerPort;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;

/**
 * 왜: 운영 환경에서 실제 Docker Socket을 통해 동기화 컨테이너를 제어하기 위함.
 */
@IfBuildProfile("prod")
@ApplicationScoped
public class ProdDockerAdapter implements DockerPort {

    private static final Logger log = Logger.getLogger(ProdDockerAdapter.class);
    private static final int DEFAULT_TIMEOUT_SECONDS = 180;
    private final DockerClient dockerClient;
    private final AppConfig.DockerConfig dockerConfig;

    @Inject
    public ProdDockerAdapter(AppConfig appConfig) {
        this.dockerConfig = appConfig.docker();
        String host = dockerConfig.host();
        var config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(host)
                .build();
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();
        this.dockerClient = DockerClientImpl.getInstance(config, httpClient);
    }

    @Override
    public String runSyncContainer() {
        String image = dockerConfig.image();
        int waitSeconds = dockerConfig.syncWaitSeconds();
        CreateContainerResponse container = dockerClient.createContainerCmd(image)
                .withCmd("sh", "-c", "sleep " + waitSeconds)
                .exec();
        String id = container.getId();
        dockerClient.startContainerCmd(id).exec();
        return id;
    }

    @Override
    public void stopAndCleanup(String containerId) {
        try {
            dockerClient.stopContainerCmd(containerId)
                    .withTimeout(DEFAULT_TIMEOUT_SECONDS)
                    .exec();
        } catch (NotModifiedException e) {
            log.debugf("Container already stopped: %s", containerId);
        } catch (NotFoundException e) {
            log.debugf("Container not found: %s", containerId);
        }
        try {
            dockerClient.removeContainerCmd(containerId)
                    .withForce(true)
                    .withRemoveVolumes(true)
                    .exec();
        } catch (NotFoundException e) {
            log.debugf("Container already removed: %s", containerId);
        }
    }

    @Override
    public boolean isRunning(String containerId) {
        try {
            return Boolean.TRUE.equals(dockerClient.inspectContainerCmd(containerId).exec().getState().getRunning());
        } catch (NotFoundException e) {
            return false;
        }
    }
}
