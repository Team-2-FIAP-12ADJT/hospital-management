package com.fiap.hospital.notification.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fiap.hospital.notification.idempotency.IdempotencyService;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class SmokeConsumerTest {

    private static final String SENSITIVE_MARKER = "smoke-secret-marker";
    // Sem hífen: precisa ser um token bare que o Jackson cite por inteiro ao recusar.
    private static final String SENSITIVE_TOKEN = "smokeSecretToken";

    @Mock
    private IdempotencyService idempotencyService;

    @Test
    void discardsMalformedJson() {
        receive("{not-json");

        verifyNoInteractions(idempotencyService);
    }

    // O marcador tem de estar DENTRO do token que o Jackson recusa: "{not-json-<marcador>"
    // falha já no `n` logo após a chave, e a mensagem cita só esse começo — o teste
    // passava igual com o payload sendo logado. Token sem hífen para o Jackson
    // reproduzi-lo inteiro em "Unrecognized token".
    @Test
    void discardsMalformedJsonWithoutLoggingItsContents(CapturedOutput output) {
        receive("{\"email\":" + SENSITIVE_TOKEN + "}");

        verifyNoInteractions(idempotencyService);
        assertThat(output.getOut()).doesNotContain(SENSITIVE_TOKEN);
    }

    @Test
    void discardsEnvelopeMissingRequiredField() {
        receive("{\"eventId\":\"" + UUID.randomUUID() + "\"}");

        verifyNoInteractions(idempotencyService);
    }

    @Test
    void discardsEnvelopeMissingRequiredFieldWithoutLoggingItsContents(CapturedOutput output) {
        receive("{\"eventId\":\"" + UUID.randomUUID() + "\",\"note\":\"" + SENSITIVE_MARKER + "\"}");

        verifyNoInteractions(idempotencyService);
        assertThat(output.getOut()).doesNotContain(SENSITIVE_MARKER);
    }

    @Test
    void discardsInvalidEventId() {
        receive("{\"eventId\":\"1-1-1-1-1\",\"eventType\":\"PatientRegistered\"}");

        verifyNoInteractions(idempotencyService);
    }

    @Test
    void discardsInvalidEventIdWithoutLoggingItsContents(CapturedOutput output) {
        receive("{\"eventId\":\"1-1-1-1-1\",\"eventType\":\"" + SENSITIVE_MARKER + "\"}");

        verifyNoInteractions(idempotencyService);
        assertThat(output.getOut()).doesNotContain(SENSITIVE_MARKER);
    }

    // O caminho de sucesso também loga, e o eventType que ele imprimia vinha do
    // envelope: sem este teste, só os caminhos de descarte estavam protegidos.
    @Test
    void acceptedEventIsLoggedWithoutItsEventType(CapturedOutput output) {
        doAnswer(invocation -> {
            invocation.getArgument(2, Runnable.class).run();
            return null;
        }).when(idempotencyService).process(any(), any(), any());

        receive("{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"" + SENSITIVE_MARKER + "\"}");

        assertThat(output.getOut()).doesNotContain(SENSITIVE_MARKER);
    }

    private void receive(String value) {
        new SmokeConsumer(JsonMapper.builder().build(), idempotencyService)
                .receive(new ConsumerRecord<>("hospital.person", 0, 0L, null, value));
    }
}
