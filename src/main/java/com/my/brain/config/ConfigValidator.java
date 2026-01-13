package com.my.brain.config;

import io.quarkus.runtime.Startup;
import io.quarkus.runtime.configuration.ProfileManager;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.nio.file.Files;
import java.nio.file.Path;

@Startup
@ApplicationScoped
public class ConfigValidator {

    private static final Logger log = Logger.getLogger(ConfigValidator.class);

    private final AppConfig appConfig;

    public ConfigValidator(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    @PostConstruct
    void validate() {
        boolean isProd = "prod".equals(ProfileManager.getActiveProfile());
        validateRequired("OPENAI_API_KEY", appConfig.openai().apiKey().orElse(null), isProd);
        validatePath("VAULT_PATH", appConfig.paths().vaultPath(), isProd);
        validatePath("TEMPLATE_PATH", appConfig.paths().templatePath(), isProd);
        validatePath("GOOGLE_CREDENTIAL_PATH", appConfig.google().credentialPath(), isProd);
    }

    private void validateRequired(String name, String value, boolean strict) {
        if (value == null || value.isBlank()) {
            String message = "필수 설정이 비어 있습니다: " + name;
            if (strict) {
                throw new IllegalStateException(message);
            }
            log.warn(message);
        }
    }

    private void validatePath(String name, String path, boolean strict) {
        Path resolved = Path.of(path);
        if (!Files.exists(resolved)) {
            String message = "경로가 존재하지 않습니다: " + name + "=" + path;
            if (strict) {
                throw new IllegalStateException(message);
            }
            log.warn(message);
        }
    }
}
