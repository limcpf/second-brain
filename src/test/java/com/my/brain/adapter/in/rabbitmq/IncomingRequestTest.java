package com.my.brain.adapter.in.rabbitmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import com.my.brain.domain.model.BrainRequest;
import com.my.brain.domain.model.MessageType;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IncomingRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mapsValidPayload() throws Exception {
        String payload = "{" +
                "\"eventId\":\"evt-1\"," +
                "\"timestamp\":\"2026-01-13T01:02:03+09:00\"," +
                "\"userId\":\"user-1\"," +
                "\"type\":\"CHAT\"," +
                "\"content\":\"hello\"" +
                "}";

        IncomingRequest incoming = objectMapper.readValue(payload, IncomingRequest.class);
        BrainRequest request = incoming.toBrainRequest();

        assertThat(request.eventId()).isEqualTo("evt-1");
        assertThat(request.userId()).isEqualTo("user-1");
        assertThat(request.type()).isEqualTo(MessageType.CHAT);
        assertThat(request.content()).isEqualTo("hello");
        assertThat(request.timestamp()).isEqualTo(OffsetDateTime.parse("2026-01-13T01:02:03+09:00"));
    }

    @Test
    void rejectsMissingFields() {
        String payload = "{" +
                "\"timestamp\":\"2026-01-13T01:02:03+09:00\"," +
                "\"userId\":\"user-1\"," +
                "\"type\":\"CHAT\"," +
                "\"content\":\"hello\"" +
                "}";

        assertThrows(ValueInstantiationException.class,
                () -> objectMapper.readValue(payload, IncomingRequest.class));
    }
}
