package com.fiap.hospital.identity.accounts.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class PersonEventConsumerTest {

    // eventType e string arbitraria do publicador (nao validada contra lista
    // nenhuma): usa o marcador no proprio eventType para provar que o log do
    // caminho "nao suportado" nao ecoa dado nenhum do envelope recusado.
    private static final String SENSITIVE_MARKER = "cpf-52998224726-marker";

    @Mock
    private ProvisionAccountFromPersonEvent provisionAccount;

    @Test
    void propagatesMalformedJsonForTheContainerErrorHandlerToClassify() {
        // Sem catch de RuntimeException: o error handler do container (KafkaConfig)
        // e quem classifica e decide retry/DLT. Engolir aqui deixaria a DLT morta.
        assertThatThrownBy(() -> receive("{not-json")).isInstanceOf(RuntimeException.class);

        verifyNoInteractions(provisionAccount);
    }

    @Test
    void propagatesEnvelopeMissingRequiredFieldForTheContainerErrorHandlerToClassify() {
        assertThatThrownBy(() -> receive("{\"eventId\":\"" + UUID.randomUUID() + "\"}"))
            .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(provisionAccount);
    }

    @Test
    void ignoresUnsupportedEventType() {
        receive(PersonEventFixtures.contactUpdated(UUID.randomUUID(), UUID.randomUUID()));

        verifyNoInteractions(provisionAccount);
    }

    @Test
    void unsupportedEventTypeIsIgnoredWithoutLoggingTheEventTypeItself(CapturedOutput output) {
        receive("{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"" + SENSITIVE_MARKER + "\"}");

        verifyNoInteractions(provisionAccount);
        assertThat(output.getOut()).doesNotContain(SENSITIVE_MARKER);
    }

    @Test
    void provisionsPatientRegisteredFromKafkaRecord() {
        UUID eventId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();

        receive(PersonEventFixtures.patientRegistered(eventId, patientId, "52998224726"));

        verify(provisionAccount).provision(any(PersonRegistration.class));
    }

    private void receive(String value) {
        new PersonEventConsumer(new PersonRegistrationParser(), provisionAccount)
            .receive(new ConsumerRecord<>("hospital.person", 0, 0L, null, value));
    }
}
