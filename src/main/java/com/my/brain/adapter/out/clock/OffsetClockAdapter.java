package com.my.brain.adapter.out.clock;

import com.my.brain.domain.port.out.ClockPort;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 왜: 시스템 시간을 주입형으로 제공해 테스트와 시간대 변환 일관성을 확보하기 위함.
 */
public class OffsetClockAdapter implements ClockPort {

    private final ZoneId zoneId;

    private OffsetClockAdapter(ZoneId zoneId) {
        this.zoneId = zoneId;
    }

    public static OffsetClockAdapter system() {
        return new OffsetClockAdapter(ZoneId.of("Asia/Seoul"));
    }

    @Override
    public OffsetDateTime now() {
        return OffsetDateTime.now(zoneId);
    }
}
