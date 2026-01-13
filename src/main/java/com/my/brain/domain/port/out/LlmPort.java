package com.my.brain.domain.port.out;

import com.my.brain.domain.model.BrainRequest;
import com.my.brain.domain.model.LlmIntentResult;

/**
 * 왜: LLM 호출을 추상화하여 도메인이 공급자나 프로토콜에 의존하지 않도록 하기 위함.
 */
public interface LlmPort {
    LlmIntentResult parseIntent(BrainRequest request);
}
