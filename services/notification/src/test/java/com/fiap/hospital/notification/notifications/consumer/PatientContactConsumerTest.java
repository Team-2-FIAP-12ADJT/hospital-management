package com.fiap.hospital.notification.notifications.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fiap.hospital.notification.notifications.service.MaintainContactReplica;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class PatientContactConsumerTest {

    // eventType e string arbitraria do publicador: o marcador vai nele mesmo
    // para provar que o log do caminho "fora de escopo" nao o ecoa.
    private static final String SENSITIVE_MARKER = "contact-secret-marker";

    @Mock
    private MaintainContactReplica maintainContactReplica;

    @Test
    void propagatesMalformedContactEventForKafkaRetryAndDlt() {
        assertThatThrownBy(() -> receive("{not-json"))
            .isInstanceOf(RuntimeException.class);

        verifyNoInteractions(maintainContactReplica);
    }

    @Test
    void ignoresContactEventOutsideReplicaScope() {
        assertThatCode(() -> receive(
            "{\"eventId\":\"00000000-0000-4000-8000-000000000001\","
                + "\"eventType\":\"DoctorRegistered\","
                + "\"occurredAt\":\"2026-09-07T12:00:00Z\","
                + "\"data\":{}}"
        )).doesNotThrowAnyException();

        verifyNoInteractions(maintainContactReplica);
    }

    @Test
    void ignoredEventOutsideScopeIsNotLoggedWithItsEventType(CapturedOutput output) {
        assertThatCode(() -> receive(
            "{\"eventId\":\"00000000-0000-4000-8000-000000000001\","
                + "\"eventType\":\"" + SENSITIVE_MARKER + "\","
                + "\"occurredAt\":\"2026-09-07T12:00:00Z\","
                + "\"data\":{}}"
        )).doesNotThrowAnyException();

        verifyNoInteractions(maintainContactReplica);
        assertThat(output.getOut()).doesNotContain(SENSITIVE_MARKER);
    }

    private void receive(String value) {
        new PatientContactConsumer(new PatientContactParser(), maintainContactReplica)
            .receive(new ConsumerRecord<>("hospital.person", 0, 0L, null, value));
    }
}
