package com.fiap.hospital.notification.notifications.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fiap.hospital.notification.notifications.service.ScheduledAppointment;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AppointmentEventParserTest {

    private final AppointmentEventParser parser = new AppointmentEventParser();

    @Test
    void readsTheFieldsTheNotificationNeeds() {
        UUID eventId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();

        ScheduledAppointment appointment = parser.parse(
            EventFixtures.appointmentScheduled(eventId, appointmentId, patientId)
        );

        assertThat(appointment.eventId()).isEqualTo(eventId);
        assertThat(appointment.appointmentId()).isEqualTo(appointmentId);
        assertThat(appointment.patientId()).isEqualTo(patientId);
        assertThat(appointment.scheduledAt()).isEqualTo(EventFixtures.SCHEDULED_AT);
        assertThat(appointment.doctorName()).isEqualTo("Dra. Helena Prado");
        assertThat(appointment.doctorSpecialty()).isEqualTo("Cardiologia");
    }

    @Test
    void refusesTheOtherAppointmentEvents() {
        String cancelled = EventFixtures
            .appointmentScheduled(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
            .replace("AppointmentScheduled", "AppointmentCancelled");

        assertThatThrownBy(() -> parser.parse(cancelled))
            .isInstanceOf(UnsupportedEventException.class)
            .hasMessage("AppointmentCancelled");
    }

    @Test
    void refusesEnvelopeWithoutData() {
        assertThatThrownBy(() -> parser.parse("""
            {"eventId":"%s","eventType":"AppointmentScheduled","occurredAt":"%s"}
            """.formatted(UUID.randomUUID(), EventFixtures.OCCURRED_AT)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("data");
    }

    @Test
    void refusesPayloadMissingTheDoctorDisplayFields() {
        String withoutSpecialty = EventFixtures
            .appointmentScheduled(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
            .replace("\"doctorSpecialty\": \"Cardiologia\"", "\"doctorSpecialty\": null");

        assertThatThrownBy(() -> parser.parse(withoutSpecialty))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("doctorSpecialty");
    }

    @Test
    void refusesMalformedJson() {
        assertThatThrownBy(() -> parser.parse("{not-json"))
            .isInstanceOf(RuntimeException.class);
    }
}
