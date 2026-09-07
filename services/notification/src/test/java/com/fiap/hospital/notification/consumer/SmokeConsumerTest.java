package com.fiap.hospital.notification.consumer;

import static org.mockito.Mockito.verifyNoInteractions;

import com.fiap.hospital.notification.idempotency.IdempotencyService;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class SmokeConsumerTest {

    @Mock
    private IdempotencyService idempotencyService;

    @Test
    void discardsMalformedJson() {
        receive("{not-json");

        verifyNoInteractions(idempotencyService);
    }

    @Test
    void discardsEnvelopeMissingRequiredField() {
        receive("{\"eventId\":\"" + UUID.randomUUID() + "\"}");

        verifyNoInteractions(idempotencyService);
    }

    @Test
    void discardsInvalidEventId() {
        receive("{\"eventId\":\"1-1-1-1-1\",\"eventType\":\"PatientRegistered\"}");

        verifyNoInteractions(idempotencyService);
    }

    private void receive(String value) {
        new SmokeConsumer(JsonMapper.builder().build(), idempotencyService)
                .receive(new ConsumerRecord<>("hospital.person", 0, 0L, null, value));
    }
}
