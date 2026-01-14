package com.my.brain.adapter.out.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.RemoveContainerCmd;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.command.StopContainerCmd;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.Volume;
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
import java.util.List;

/**
 * 왜: 운영 환경에서 실제 Docker Socket을 통해 동기화 컨테이너를 제어하기 위함.
 */
@IfBuildProfile("prod")
@ApplicationScoped
public class ProdDockerAdapter implements DockerPort {

    private static final Logger log = Logger.getLogger(ProdDockerAdapter.class);
    private static final int DEFAULT_TIMEOUT_SECONDS = 180;
    private static final long DEFAULT_SHM_BYTES = 2L * 1024 * 1024 * 1024;

    private final DockerClient dockerClient;
    private final AppConfig.DockerConfig dockerConfig;
    private final AppConfig.PathsConfig pathsConfig;

    @Inject
    public ProdDockerAdapter(AppConfig appConfig) {
        this(appConfig.docker(), appConfig.paths(), createDockerClient(appConfig.docker()));
    }

    ProdDockerAdapter(AppConfig.DockerConfig dockerConfig, AppConfig.PathsConfig pathsConfig, DockerClient dockerClient) {
        this.dockerConfig = dockerConfig;
        this.pathsConfig = pathsConfig;
        this.dockerClient = dockerClient;
    }

    private static DockerClient createDockerClient(AppConfig.DockerConfig dockerConfig) {
        var config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerConfig.host())
                .build();
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();
        return DockerClientImpl.getInstance(config, httpClient);
    }

    @Override
    public String runSyncContainer() {
        String image = dockerConfig.image();
        int waitSeconds = dockerConfig.syncWaitSeconds();

        HostConfig hostConfig = HostConfig.newHostConfig()
                .withBinds(
                        new Bind(pathsConfig.vaultPath(), new Volume("/vault")),
                        new Bind(dockerConfig.configPath(), new Volume("/config"))
                )
                .withShmSize(DEFAULT_SHM_BYTES)
                .withSecurityOpts(List.of("seccomp:unconfined"))
                .withPortBindings(portBindings());

        List<String> env = List.of(
                "PUID=" + dockerConfig.puid(),
                "PGID=" + dockerConfig.pgid(),
                "TZ=" + dockerConfig.timezone()
        );

        ExposedPort port3000 = ExposedPort.tcp(3000);
        ExposedPort port3001 = ExposedPort.tcp(3001);

        CreateContainerCmd createContainerCmd = dockerClient.createContainerCmd(image)
                .withEnv(env)
                .withHostConfig(hostConfig)
                .withExposedPorts(port3000, port3001);

        CreateContainerResponse container = createContainerCmd.exec();
        String id = container.getId();

        StartContainerCmd startContainerCmd = dockerClient.startContainerCmd(id);
        startContainerCmd.exec();

        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(Duration.ofSeconds(waitSeconds));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            stopAndCleanup(id);
        });

        return id;
    }

    private Ports portBindings() {
        ExposedPort port3000 = ExposedPort.tcp(3000);
        ExposedPort port3001 = ExposedPort.tcp(3001);
        Ports ports = new Ports();
        ports.bind(port3000, Ports.Binding.bindPort(3000));
        ports.bind(port3001, Ports.Binding.bindPort(3001));
        return ports;
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
