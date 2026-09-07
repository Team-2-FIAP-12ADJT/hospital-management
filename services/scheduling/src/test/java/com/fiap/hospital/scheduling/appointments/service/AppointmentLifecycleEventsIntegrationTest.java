package com.fiap.hospital.scheduling.appointments.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fiap.hospital.scheduling.appointments.domain.Appointment;
import com.fiap.hospital.scheduling.appointments.repository.AppointmentRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@Testcontainers
class AppointmentLifecycleEventsIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    private static final UUID PATIENT = UUID.fromString("00000000-0000-4000-8000-000000000003");
    private static final UUID DOCTOR = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final Instant SLOT = Instant.parse("2030-01-01T13:00:00Z");

    @Autowired
    private AppointmentSchedulingService service;

    @Autowired
    private AppointmentRepository repository;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private JsonMapper mapper;

    @BeforeEach
    void clean() {
        repository.deleteAll();
        jdbc.sql("DELETE FROM public.outbox_events").update();
    }

    @Test
    void reschedulingCarriesThePreviousTimeSoTheReminderCanBeSwapped() {
        Appointment appointment = service.schedule(PATIENT, DOCTOR, SLOT, false, null);
        Instant newSlot = SLOT.plusSeconds(3600);

        service.reschedule(appointment.getId(), newSlot, true, "encaixe autorizado");

        JsonNode data = payloadOf(appointment.getId(), "AppointmentRescheduled");
        assertThat(data.get("previousScheduledAt").asString())
            .as("sem o horario anterior o consumidor nao sabe qual lembrete cancelar")
            .isEqualTo("2030-01-01T13:00:00.000Z");
        assertThat(data.get("scheduledAt").asString()).isEqualTo("2030-01-01T14:00:00.000Z");
        assertThat(data.get("status").asString()).isEqualTo("SCHEDULED");
        assertThat(data.get("fitIn").asBoolean()).isTrue();
        assertThat(data.get("fitInReason").asString()).isEqualTo("encaixe autorizado");
        assertThat(data.get("patientName").asString()).isEqualTo("Marcos Vieira");
        assertThat(data.get("doctorName").asString()).isEqualTo("Dra. Helena Prado");
        assertThat(data.get("doctorSpecialty").asString()).isEqualTo("Cardiologia");
    }

    @Test
    void cancellingCarriesTheCancellationInstantAndNoDisplayData() {
        Appointment appointment = service.schedule(PATIENT, DOCTOR, SLOT, false, null);

        service.cancel(appointment.getId());

        JsonNode data = payloadOf(appointment.getId(), "AppointmentCancelled");
        assertThat(data.get("status").asString()).isEqualTo("CANCELLED");
        assertThat(data.get("scheduledAt").asString()).isEqualTo("2030-01-01T13:00:00.000Z");
        assertThat(data.get("cancelledAt").asString())
            .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z");
        assertThat(data.has("patientName"))
            .as("a projecao ja materializou a consulta; aqui so muda o estado")
            .isFalse();
        assertThat(data.has("doctorName")).isFalse();
    }

    @Test
    void completingCarriesTheCompletionInstantAndNoDisplayData() {
        Appointment appointment = service.schedule(PATIENT, DOCTOR, SLOT, false, null);

        service.complete(appointment.getId());

        JsonNode data = payloadOf(appointment.getId(), "AppointmentCompleted");
        assertThat(data.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(data.get("completedAt").asString())
            .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z");
        assertThat(data.has("patientName")).isFalse();
    }

    @Test
    void repeatedCompletionDoesNotPublishASecondEvent() {
        Appointment appointment = service.schedule(PATIENT, DOCTOR, SLOT, false, null);

        assertThat(service.complete(appointment.getId())).isTrue();
        assertThat(service.complete(appointment.getId())).isFalse();

        assertThat(countOf(appointment.getId(), "AppointmentCompleted"))
            .as("conclusao e idempotente: o mesmo fato nao vira dois eventos")
            .isEqualTo(1L);
    }

    @Test
    void cancellingAfterReschedulingKeepsBothEventsOnTheSameAggregate() {
        Appointment appointment = service.schedule(PATIENT, DOCTOR, SLOT, false, null);

        service.reschedule(appointment.getId(), SLOT.plusSeconds(7200), false, null);
        service.cancel(appointment.getId());

        assertThat(countOf(appointment.getId(), "AppointmentScheduled")).isEqualTo(1L);
        assertThat(countOf(appointment.getId(), "AppointmentRescheduled")).isEqualTo(1L);
        assertThat(countOf(appointment.getId(), "AppointmentCancelled")).isEqualTo(1L);
        assertThat(jdbc.sql("""
            SELECT count(DISTINCT topic) FROM public.outbox_events
            WHERE aggregate_id = :appointment
            """)
            .param("appointment", appointment.getId())
            .query(Long.class)
            .single())
            .as("os quatro eventos da consulta vivem no mesmo topico, com a mesma key")
            .isEqualTo(1L);
    }

    @Test
    void completedAtIsPersistedAlongsideTheStatus() {
        Appointment appointment = service.schedule(PATIENT, DOCTOR, SLOT, false, null);

        service.complete(appointment.getId());

        assertThat(repository.findById(appointment.getId()).orElseThrow().getCompletedAt())
            .as("o instante da conclusao vive na linha, nao apenas no evento")
            .isNotNull();
    }

    private long countOf(UUID appointmentId, String eventType) {
        return jdbc.sql("""
            SELECT count(*) FROM public.outbox_events
            WHERE aggregate_type = 'appointment'
              AND aggregate_id = :appointment
              AND type = :type
              AND version = 1
              AND topic = 'hospital.appointment'
            """)
            .param("appointment", appointmentId)
            .param("type", eventType)
            .query(Long.class)
            .single();
    }

    private JsonNode payloadOf(UUID appointmentId, String eventType) {
        assertThat(countOf(appointmentId, eventType)).isEqualTo(1L);

        String envelope = jdbc.sql("""
            SELECT envelope FROM public.outbox_events
            WHERE aggregate_id = :appointment AND type = :type
            """)
            .param("appointment", appointmentId)
            .param("type", eventType)
            .query(String.class)
            .single();

        JsonNode root = mapper.readTree(envelope);
        assertThat(root.get("eventType").asString()).isEqualTo(eventType);
        assertThat(root.get("eventVersion").asInt()).isEqualTo(1);
        assertThat(root.get("occurredAt").asString())
            .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z");
        assertThat(root.get("data").get("appointmentId").asString())
            .isEqualTo(appointmentId.toString());
        assertThat(root.get("data").get("patientId").asString()).isEqualTo(PATIENT.toString());
        assertThat(root.get("data").get("doctorId").asString()).isEqualTo(DOCTOR.toString());
        return root.get("data");
    }
}
