package com.my.brain.domain.port.out;

import java.time.OffsetDateTime;

/**
 * 왜: 현재 시간을 주입형으로 분리하여 테스트 가능성과 시간대 변환 로직을 단순화하기 위함.
 */
public interface ClockPort {
    OffsetDateTime now();
}
