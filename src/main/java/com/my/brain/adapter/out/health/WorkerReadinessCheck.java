package com.my.brain.adapter.out.health;

import com.my.brain.config.AppConfig;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import java.nio.file.Files;
import java.nio.file.Path;

@Readiness
@ApplicationScoped
public class WorkerReadinessCheck implements HealthCheck {

    private final AppConfig appConfig;

    public WorkerReadinessCheck(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    @Override
    public HealthCheckResponse call() {
        Path vault = Path.of(appConfig.paths().vaultPath());
        Path template = Path.of(appConfig.paths().templatePath());
        boolean vaultOk = Files.exists(vault);
        boolean templateOk = Files.exists(template);
        return HealthCheckResponse.named("worker-readiness")
                .withData("vaultPath", vault.toString())
                .withData("templatePath", template.toString())
                .withData("vaultExists", vaultOk)
                .withData("templateExists", templateOk)
                .status(vaultOk && templateOk)
                .build();
    }
}
