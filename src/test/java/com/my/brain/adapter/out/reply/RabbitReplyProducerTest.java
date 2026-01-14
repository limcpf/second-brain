package com.my.brain.adapter.out.reply;

import com.my.brain.domain.model.ReplyMessage;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@SuppressWarnings({"unchecked", "rawtypes"})
class RabbitReplyProducerTest {

    @Test
    void send_serializes_payload_and_emits() {
        @SuppressWarnings("unchecked")
        Emitter<String> emitter = (Emitter<String>) mock(Emitter.class);
        RabbitReplyProducer producer = new RabbitReplyProducer(emitter);
        ReplyMessage message = new ReplyMessage("user", "hello");

        producer.send(message);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(emitter).send(payloadCaptor.capture());
        assertEquals("{\"replyToUserId\":\"user\",\"content\":\"hello\"}", payloadCaptor.getValue());
    }
}
