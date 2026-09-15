package com.fiap.hospital.notification.notifications.consumer;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fiap.hospital.notification.notifications.service.AppointmentNotifications;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AppointmentEventConsumerTest {

    @Mock
    private AppointmentNotifications appointmentNotifications;

    @Test
    void propagatesMalformedAppointmentEventForKafkaRetryAndDlt() {
        assertThatThrownBy(() -> receive("{not-json"))
            .isInstanceOf(RuntimeException.class);

        verifyNoInteractions(appointmentNotifications);
    }

    @Test
    void ignoresAppointmentEventOutsideNotificationScope() {
        assertThatCode(() -> receive("""
            {"eventId":"00000000-0000-4000-8000-000000000001",
             "eventType":"AppointmentCompleted",
             "occurredAt":"2026-09-07T12:00:00Z",
             "data":{"appointmentId":"00000000-0000-4000-8000-000000000002",
                    "patientId":"00000000-0000-4000-8000-000000000003"}}
            """))
            .doesNotThrowAnyException();

        verifyNoInteractions(appointmentNotifications);
    }

    private void receive(String value) {
        new AppointmentEventConsumer(new AppointmentEventParser(), appointmentNotifications)
            .receive(new ConsumerRecord<>("hospital.appointment", 0, 0L, null, value));
    }
}
