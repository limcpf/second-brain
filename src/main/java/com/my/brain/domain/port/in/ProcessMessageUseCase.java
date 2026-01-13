package com.my.brain.domain.port.in;

import com.my.brain.domain.model.BrainRequest;
import com.my.brain.domain.model.ReplyMessage;

/**
 * 왜: 외부 입력을 도메인 진입점 하나로 수렴시켜 의도 파악과 후속 작업을 일관되게 처리하기 위함.
 */
public interface ProcessMessageUseCase {
    ReplyMessage process(BrainRequest request);
}
