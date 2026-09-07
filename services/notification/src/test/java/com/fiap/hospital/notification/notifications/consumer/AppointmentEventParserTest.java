package com.fiap.hospital.notification.notifications.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fiap.hospital.notification.notifications.service.AppointmentEvent;
import com.fiap.hospital.notification.notifications.service.CancelledAppointment;
import com.fiap.hospital.notification.notifications.service.RescheduledAppointment;
import com.fiap.hospital.notification.notifications.service.ScheduledAppointment;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AppointmentEventParserTest {

    private final AppointmentEventParser parser = new AppointmentEventParser();

    @Test
    void readsTheFieldsTheNotificationNeeds() {
        UUID eventId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();

        AppointmentEvent parsed = parser.parse(
            EventFixtures.appointmentScheduled(eventId, appointmentId, patientId)
        );

        assertThat(parsed).isInstanceOf(ScheduledAppointment.class);
        ScheduledAppointment appointment = (ScheduledAppointment) parsed;
        assertThat(appointment.eventId()).isEqualTo(eventId);
        assertThat(appointment.appointmentId()).isEqualTo(appointmentId);
        assertThat(appointment.patientId()).isEqualTo(patientId);
        assertThat(appointment.scheduledAt()).isEqualTo(EventFixtures.SCHEDULED_AT);
        assertThat(appointment.doctorName()).isEqualTo("Dra. Helena Prado");
        assertThat(appointment.doctorSpecialty()).isEqualTo("Cardiologia");
    }

    @Test
    void readsThePreviousTimeFromTheRescheduledEvent() {
        UUID appointmentId = UUID.randomUUID();
        Instant previous = EventFixtures.SCHEDULED_AT;
        Instant novo = previous.plus(Duration.ofHours(2));

        AppointmentEvent event = parser.parse(EventFixtures.appointmentRescheduled(
            UUID.randomUUID(), appointmentId, UUID.randomUUID(), previous, novo
        ));

        assertThat(event).isInstanceOf(RescheduledAppointment.class);
        RescheduledAppointment rescheduled = (RescheduledAppointment) event;
        assertThat(rescheduled.previousScheduledAt()).isEqualTo(previous);
        assertThat(rescheduled.scheduledAt()).isEqualTo(novo);
        assertThat(rescheduled.appointmentId()).isEqualTo(appointmentId);
        assertThat(rescheduled.doctorName()).isEqualTo("Dra. Helena Prado");
    }

    @Test
    void readsTheCancelledEventWithoutDisplayData() {
        UUID appointmentId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();

        AppointmentEvent event = parser.parse(
            EventFixtures.appointmentCancelled(UUID.randomUUID(), appointmentId, patientId)
        );

        assertThat(event).isInstanceOf(CancelledAppointment.class);
        assertThat(event.appointmentId()).isEqualTo(appointmentId);
        assertThat(((CancelledAppointment) event).patientId()).isEqualTo(patientId);
    }

    @Test
    void refusesAppointmentCompletedBecauseItIsOnlyForTheProjection() {
        String completed = EventFixtures.appointmentCompleted(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()
        );

        assertThatThrownBy(() -> parser.parse(completed))
            .as("o lembrete já saiu antes da consulta acontecer")
            .isInstanceOf(UnsupportedEventException.class)
            .hasMessage("AppointmentCompleted");
    }

    @Test
    void refusesAnUnknownAppointmentEvent() {
        String unknown = EventFixtures
            .appointmentScheduled(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
            .replace("AppointmentScheduled", "AppointmentVaporized");

        assertThatThrownBy(() -> parser.parse(unknown))
            .isInstanceOf(UnsupportedEventException.class)
            .hasMessage("AppointmentVaporized");
    }

    @Test
    void refusesARescheduledEventWithoutThePreviousTime() {
        String withoutPrevious = EventFixtures.appointmentRescheduled(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            EventFixtures.SCHEDULED_AT, EventFixtures.SCHEDULED_AT.plus(Duration.ofHours(2))
        ).replaceAll("\"previousScheduledAt\": \"[^\"]*\"", "\"previousScheduledAt\": null");

        assertThatThrownBy(() -> parser.parse(withoutPrevious))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("previousScheduledAt");
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
