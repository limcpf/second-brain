package com.my.brain.adapter.out.telegram;

import com.my.brain.config.AppConfig;
import com.my.brain.domain.service.TelegramUpdateService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 왜: 텔레그램 업데이트를 주기적으로 폴링해 RabbitMQ로 재전달하기 위한 작업 스케줄러가 필요하기 때문.
 */
@ApplicationScoped
public class TelegramUpdatePoller {

    private static final Logger log = Logger.getLogger(TelegramUpdatePoller.class);

    private final TelegramUpdateService telegramUpdateService;
    private final int pollIntervalSeconds;
    private final int pollTimeoutSeconds;
    private final ScheduledExecutorService executor;
    private volatile long offset = 0L;

    @Inject
    public TelegramUpdatePoller(TelegramUpdateService telegramUpdateService, AppConfig appConfig) {
        this.telegramUpdateService = telegramUpdateService;
        this.pollIntervalSeconds = appConfig.telegram().pollIntervalSeconds();
        this.pollTimeoutSeconds = appConfig.telegram().pollTimeoutSeconds();
        this.executor = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().name("telegram-poller-", 0).factory());
    }

    @PostConstruct
    void start() {
        executor.scheduleWithFixedDelay(this::pollSafely, 0, pollIntervalSeconds, TimeUnit.SECONDS);
    }

    private void pollSafely() {
        try {
            offset = telegramUpdateService.fetchAndPublish(offset, pollTimeoutSeconds);
        } catch (Exception e) {
            log.warnf("텔레그램 폴링 중 예외: %s", e.getMessage());
        }
    }

    @PreDestroy
    void stop() {
        executor.shutdownNow();
    }
}
