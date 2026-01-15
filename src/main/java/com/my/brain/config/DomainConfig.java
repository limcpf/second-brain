package com.my.brain.config;

import com.my.brain.adapter.out.clock.OffsetClockAdapter;
import com.my.brain.domain.port.in.ProcessMessageUseCase;
import com.my.brain.domain.port.in.RelayTelegramMessageUseCase;
import com.my.brain.domain.port.out.ClockPort;
import com.my.brain.domain.port.out.DockerPort;
import com.my.brain.domain.port.out.FilePort;
import com.my.brain.domain.port.out.GooglePort;
import com.my.brain.domain.port.out.LlmPort;
import com.my.brain.domain.port.out.ReplyPort;
import com.my.brain.domain.port.out.TelegramIncomingPublishPort;
import com.my.brain.domain.port.out.TelegramSendPort;
import com.my.brain.domain.port.out.TelegramUpdatePort;
import com.my.brain.domain.service.ProcessMessageService;
import com.my.brain.domain.service.TelegramRelayService;
import com.my.brain.domain.service.TelegramUpdateService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * 왜: 도메인 서비스와 포트 구현을 명시적으로 연결하여 헥사고날 구조를 보장하기 위함.
 */
@ApplicationScoped
public class DomainConfig {

    @Produces
    @ApplicationScoped
    public ProcessMessageUseCase processMessageUseCase(LlmPort llmPort,
                                                       FilePort filePort,
                                                       GooglePort googlePort,
                                                       DockerPort dockerPort,
                                                       ReplyPort replyPort,
                                                       ClockPort clockPort) {
        return new ProcessMessageService(llmPort, filePort, googlePort, dockerPort, replyPort, clockPort);
    }

    @Produces
    @ApplicationScoped
    public RelayTelegramMessageUseCase relayTelegramMessageUseCase(TelegramSendPort telegramSendPort) {
        return new TelegramRelayService(telegramSendPort);
    }

    @Produces
    @ApplicationScoped
    public TelegramUpdateService telegramUpdateService(TelegramUpdatePort telegramUpdatePort,
                                                       TelegramIncomingPublishPort telegramIncomingPublishPort) {
        return new TelegramUpdateService(telegramUpdatePort, telegramIncomingPublishPort);
    }

    @Produces
    @ApplicationScoped
    public ClockPort clockPort() {
        return OffsetClockAdapter.system();
    }
}
