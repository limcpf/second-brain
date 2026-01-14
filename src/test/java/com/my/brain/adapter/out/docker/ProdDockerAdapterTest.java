package com.my.brain.adapter.out.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.RemoveContainerCmd;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.command.StopContainerCmd;
import com.github.dockerjava.api.model.ExposedPort;
import com.my.brain.config.AppConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProdDockerAdapterTest {

    @SuppressWarnings("unchecked")
    @Test
    void runSyncContainer_starts_and_cleans_up_after_wait() {
        DockerClient dockerClient = mock(DockerClient.class);
        CreateContainerCmd createCmd = mock(CreateContainerCmd.class);
        StartContainerCmd startCmd = mock(StartContainerCmd.class);
        StopContainerCmd stopCmd = mock(StopContainerCmd.class);
        RemoveContainerCmd removeCmd = mock(RemoveContainerCmd.class);

        CreateContainerResponse response = new CreateContainerResponse();
        response.setId("cid");

        AtomicReference<List<String>> envHolder = new AtomicReference<>();

        when(dockerClient.createContainerCmd(anyString())).thenReturn(createCmd);
        lenient().when(createCmd.withEnv(anyList())).thenAnswer(invocation -> {
            envHolder.set(invocation.getArgument(0));
            return createCmd;
        });
        lenient().when(createCmd.withHostConfig(any())).thenReturn(createCmd);
        lenient().when(createCmd.withExposedPorts(any(ExposedPort.class), any(ExposedPort.class))).thenReturn(createCmd);
        when(createCmd.exec()).thenReturn(response);

        when(dockerClient.startContainerCmd("cid")).thenReturn(startCmd);
        doNothing().when(startCmd).exec();

        when(dockerClient.stopContainerCmd("cid")).thenReturn(stopCmd);
        when(stopCmd.withTimeout(anyInt())).thenReturn(stopCmd);
        doNothing().when(stopCmd).exec();

        when(dockerClient.removeContainerCmd("cid")).thenReturn(removeCmd);
        when(removeCmd.withForce(true)).thenReturn(removeCmd);
        when(removeCmd.withRemoveVolumes(true)).thenReturn(removeCmd);
        doNothing().when(removeCmd).exec();

        AppConfig.DockerConfig dockerConfig = new StubDockerConfig();
        AppConfig.PathsConfig pathsConfig = new StubPathsConfig();

        ProdDockerAdapter adapter = new ProdDockerAdapter(dockerConfig, pathsConfig, dockerClient);

        String id = adapter.runSyncContainer();

        assertEquals("cid", id);

        verify(createCmd).withEnv(envHolder.get());
        List<String> env = envHolder.get();
        assertNotNull(env);
        assertTrue(env.contains("PUID=2001"));
        assertTrue(env.contains("PGID=2002"));
        assertTrue(env.contains("TZ=Asia/Seoul"));

        verify(startCmd).exec();
        verify(stopCmd, timeout(500)).exec();
        verify(removeCmd, timeout(500)).exec();
    }

    private static class StubDockerConfig implements AppConfig.DockerConfig {
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
            return 0;
        }

        @Override
        public String configPath() {
            return "./data/obsidian-config";
        }

        @Override
        public String puid() {
            return "2001";
        }

        @Override
        public String pgid() {
            return "2002";
        }

        @Override
        public String timezone() {
            return "Asia/Seoul";
        }
    }

    private static class StubPathsConfig implements AppConfig.PathsConfig {
        @Override
        public String vaultPath() {
            return "./data/vault";
        }

        @Override
        public String templatePath() {
            return "src/main/resources/templates";
        }
    }
}
