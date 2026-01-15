package com.my.brain.domain.port.out;

import com.my.brain.domain.model.TelegramIncomingMessage;

import java.util.List;

/**
 * 왜: 텔레그램 업데이트 조회 방법을 추상화해 폴링/웹훅 등 구현 교체 시 도메인 계약을 유지하기 위함.
 */
public interface TelegramUpdatePort {
    List<TelegramIncomingMessage> fetchUpdates(long offset, int timeoutSeconds);
}
