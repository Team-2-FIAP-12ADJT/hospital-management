package com.fiap.hospital.history.projection.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.util.ReflectionTestUtils.setField;

class AppointmentProjectionTest {

    @Test
    void gettersExposePersistedFields() {
        UUID appointmentId = UUID.fromString("10000000-0000-4000-8000-000000000003");
        UUID patientId = UUID.fromString("00000000-0000-4000-8000-000000000003");
        UUID doctorId = UUID.fromString("00000000-0000-4000-8000-000000000001");
        Instant scheduledAt = Instant.parse("2026-07-10T14:00:00Z");
        Instant cancelledAt = Instant.parse("2026-07-09T10:00:00Z");
        Instant completedAt = Instant.parse("2026-07-10T14:42:00Z");
        Instant updatedAt = Instant.parse("2026-07-10T14:43:00Z");

        AppointmentProjection projection = new AppointmentProjection();
        setField(projection, "appointmentId", appointmentId);
        setField(projection, "patientId", patientId);
        setField(projection, "doctorId", doctorId);
        setField(projection, "scheduledAt", scheduledAt);
        setField(projection, "status", AppointmentStatus.CANCELLED);
        setField(projection, "fitIn", false);
        setField(projection, "fitInReason", null);
        setField(projection, "patientName", "Marcos Vieira");
        setField(projection, "doctorName", "Dra. Helena Prado");
        setField(projection, "doctorSpecialty", "Cardiologia");
        setField(projection, "cancelledAt", cancelledAt);
        setField(projection, "completedAt", completedAt);
        setField(projection, "updatedAt", updatedAt);

        assertEquals(appointmentId, projection.getAppointmentId());
        assertEquals(patientId, projection.getPatientId());
        assertEquals(doctorId, projection.getDoctorId());
        assertEquals(scheduledAt, projection.getScheduledAt());
        assertEquals(AppointmentStatus.CANCELLED, projection.getStatus());
        assertFalse(projection.isFitIn());
        assertNull(projection.getFitInReason());
        assertEquals("Marcos Vieira", projection.getPatientName());
        assertEquals("Dra. Helena Prado", projection.getDoctorName());
        assertEquals("Cardiologia", projection.getDoctorSpecialty());
        assertEquals(cancelledAt, projection.getCancelledAt());
        assertEquals(completedAt, projection.getCompletedAt());
    }

    @Test
    void statusEnumContainsProjectionValues() {
        assertEquals(3, AppointmentStatus.values().length);
        assertEquals(AppointmentStatus.SCHEDULED, AppointmentStatus.valueOf("SCHEDULED"));
    }

    @Test
    void applyScheduledUpdatesFields() {
        AppointmentProjection projection = new AppointmentProjection();
        setField(projection, "updatedAt", Instant.parse("2026-07-09T09:00:00Z"));
        UUID id = UUID.randomUUID();
        projection.applyScheduled(id, id, id, Instant.parse("2026-07-10T14:00:00Z"), false, null, "P", "D", "S", Instant.parse("2026-07-09T10:00:00Z"));
        
        assertEquals(AppointmentStatus.SCHEDULED, projection.getStatus());
        assertEquals(Instant.parse("2026-07-10T14:00:00Z"), projection.getScheduledAt());
        assertEquals(Instant.parse("2026-07-09T10:00:00Z"), setFieldAndGet(projection, "updatedAt"));
    }

    @Test
    void applyRescheduledUpdatesFields() {
        AppointmentProjection projection = new AppointmentProjection();
        setField(projection, "updatedAt", Instant.parse("2026-07-09T09:00:00Z"));
        UUID id = UUID.randomUUID();
        projection.applyRescheduled(id, id, id, Instant.parse("2026-07-10T14:00:00Z"), false, null, "P", "D", "S", Instant.parse("2026-07-09T10:00:00Z"));
        
        assertEquals(AppointmentStatus.SCHEDULED, projection.getStatus());
        assertEquals(Instant.parse("2026-07-10T14:00:00Z"), projection.getScheduledAt());
        assertEquals(Instant.parse("2026-07-09T10:00:00Z"), setFieldAndGet(projection, "updatedAt"));
    }

    @Test
    void applyCancelledUpdatesFields() {
        AppointmentProjection projection = new AppointmentProjection();
        setField(projection, "updatedAt", Instant.parse("2026-07-09T09:00:00Z"));
        UUID id = UUID.randomUUID();
        projection.applyCancelled(id, id, id, Instant.parse("2026-07-09T10:00:00Z"), Instant.parse("2026-07-09T11:00:00Z"), Instant.parse("2026-07-09T12:00:00Z"));
        
        assertEquals(AppointmentStatus.CANCELLED, projection.getStatus());
        assertEquals(Instant.parse("2026-07-09T11:00:00Z"), projection.getCancelledAt());
        assertEquals(Instant.parse("2026-07-09T12:00:00Z"), setFieldAndGet(projection, "updatedAt"));
    }

    @Test
    void applyCompletedUpdatesFields() {
        AppointmentProjection projection = new AppointmentProjection();
        setField(projection, "updatedAt", Instant.parse("2026-07-09T09:00:00Z"));
        UUID id = UUID.randomUUID();
        projection.applyCompleted(id, id, id, Instant.parse("2026-07-09T10:00:00Z"), Instant.parse("2026-07-09T11:00:00Z"), Instant.parse("2026-07-09T12:00:00Z"));
        
        assertEquals(AppointmentStatus.COMPLETED, projection.getStatus());
        assertEquals(Instant.parse("2026-07-09T11:00:00Z"), projection.getCompletedAt());
        assertEquals(Instant.parse("2026-07-09T12:00:00Z"), setFieldAndGet(projection, "updatedAt"));
    }

    // Uma vez terminal, nenhum dos quatro caminhos reescreve a linha com carimbo
    // empatado — nem outro terminal, que e o caso de CANCELLED apos COMPLETED.
    @Test
    void noApplyOverwritesTerminalStateOnTiedTimestamp() {
        UUID id = UUID.randomUUID();
        Instant tie = Instant.parse("2026-07-09T10:00:00Z");

        AppointmentProjection afterCompleted = new AppointmentProjection();
        afterCompleted.applyCompleted(id, id, id, Instant.parse("2026-07-09T08:00:00Z"), Instant.parse("2026-07-09T09:00:00Z"), tie);
        afterCompleted.applyScheduled(id, id, id, Instant.parse("2026-07-11T14:00:00Z"), false, null, "P", "D", "S", tie);
        afterCompleted.applyCancelled(id, id, id, Instant.parse("2026-07-11T14:00:00Z"), Instant.parse("2026-07-09T11:00:00Z"), tie);
        assertEquals(AppointmentStatus.COMPLETED, afterCompleted.getStatus());

        AppointmentProjection afterCancelled = new AppointmentProjection();
        afterCancelled.applyCancelled(id, id, id, Instant.parse("2026-07-09T08:00:00Z"), Instant.parse("2026-07-09T09:00:00Z"), tie);
        afterCancelled.applyCompleted(id, id, id, Instant.parse("2026-07-09T08:00:00Z"), Instant.parse("2026-07-09T11:00:00Z"), tie);
        assertEquals(AppointmentStatus.CANCELLED, afterCancelled.getStatus());
    }

    // SCHEDULED nao e terminal: o empate continua valendo como novo, que e o caso
    // que originou a guarda de empate (agendar e cancelar no mesmo milissegundo).
    @Test
    void scheduledStateStillAcceptsTiedTerminalEvent() {
        AppointmentProjection projection = new AppointmentProjection();
        UUID id = UUID.randomUUID();
        Instant tie = Instant.parse("2026-07-09T10:00:00Z");
        projection.applyScheduled(id, id, id, Instant.parse("2026-07-11T14:00:00Z"), false, null, "P", "D", "S", tie);

        projection.applyCancelled(id, id, id, Instant.parse("2026-07-11T14:00:00Z"), Instant.parse("2026-07-09T11:00:00Z"), tie);

        assertEquals(AppointmentStatus.CANCELLED, projection.getStatus());
    }

    // Reprocessar da DLT um reschedule de mesmo occurredAt que a conclusao nao pode
    // ressuscitar SCHEDULED: o completedAt ficaria preenchido numa consulta "ativa".
    @Test
    void terminalStateIsNotReopenedByTiedTimestamp() {
        AppointmentProjection projection = new AppointmentProjection();
        UUID id = UUID.randomUUID();
        Instant tie = Instant.parse("2026-07-09T10:00:00Z");
        projection.applyCompleted(id, id, id, Instant.parse("2026-07-09T08:00:00Z"), Instant.parse("2026-07-09T09:00:00Z"), tie);

        projection.applyRescheduled(id, id, id, Instant.parse("2026-07-11T14:00:00Z"), false, null, "P", "D", "S", tie);

        assertEquals(AppointmentStatus.COMPLETED, projection.getStatus());
        assertEquals(Instant.parse("2026-07-09T09:00:00Z"), projection.getCompletedAt());
    }

    @Test
    void terminalStateStillYieldsToStrictlyNewerEvent() {
        AppointmentProjection projection = new AppointmentProjection();
        UUID id = UUID.randomUUID();
        projection.applyCancelled(id, id, id, Instant.parse("2026-07-09T08:00:00Z"), Instant.parse("2026-07-09T09:00:00Z"), Instant.parse("2026-07-09T10:00:00Z"));

        projection.applyScheduled(id, id, id, Instant.parse("2026-07-11T14:00:00Z"), false, null, "P", "D", "S", Instant.parse("2026-07-09T10:00:00.001Z"));

        assertEquals(AppointmentStatus.SCHEDULED, projection.getStatus());
    }

    @Test
    void applyIsIgnoredWhenOlderThanCurrentState() {
        AppointmentProjection projection = new AppointmentProjection();
        setField(projection, "updatedAt", Instant.parse("2026-07-09T10:00:00Z"));
        UUID id = UUID.randomUUID();

        projection.applyCancelled(id, id, id, Instant.parse("2026-07-09T10:00:00Z"), Instant.parse("2026-07-09T11:00:00Z"), Instant.parse("2026-07-09T09:00:00Z"));

        assertNull(projection.getStatus());
        assertEquals(Instant.parse("2026-07-09T10:00:00Z"), setFieldAndGet(projection, "updatedAt"));
    }

    // O envelope trunca occurredAt em milissegundos, entao dois eventos distintos do
    // mesmo agendamento cabem no mesmo carimbo. Empate tem de valer como novo, senao
    // agendar e cancelar no mesmo milissegundo deixa a projecao em SCHEDULED para
    // sempre. Reentrega do mesmo evento segue barrada pelo processed_event (eventId).
    @Test
    void applyWinsWhenTimestampTiesCurrentState() {
        AppointmentProjection projection = new AppointmentProjection();
        setField(projection, "updatedAt", Instant.parse("2026-07-09T10:00:00Z"));
        UUID id = UUID.randomUUID();

        projection.applyCancelled(id, id, id, Instant.parse("2026-07-09T10:00:00Z"), Instant.parse("2026-07-09T11:00:00Z"), Instant.parse("2026-07-09T10:00:00Z"));

        assertEquals(AppointmentStatus.CANCELLED, projection.getStatus());
        assertEquals(Instant.parse("2026-07-09T10:00:00Z"), setFieldAndGet(projection, "updatedAt"));
    }

    @Test
    void appliesScheduledAreIgnoredWhenStale() {
        AppointmentProjection projection = new AppointmentProjection();
        setField(projection, "updatedAt", Instant.parse("2026-07-09T10:00:00Z"));
        UUID id = UUID.randomUUID();
        projection.applyScheduled(id, id, id, Instant.parse("2026-07-10T14:00:00Z"), false, null, "P", "D", "S", Instant.parse("2026-07-09T09:00:00Z"));
        assertNull(projection.getStatus());
    }

    @Test
    void appliesRescheduledAreIgnoredWhenStale() {
        AppointmentProjection projection = new AppointmentProjection();
        setField(projection, "updatedAt", Instant.parse("2026-07-09T10:00:00Z"));
        UUID id = UUID.randomUUID();
        projection.applyRescheduled(id, id, id, Instant.parse("2026-07-10T14:00:00Z"), false, null, "P", "D", "S", Instant.parse("2026-07-09T09:00:00Z"));
        assertNull(projection.getStatus());
    }

    @Test
    void appliesCompletedAreIgnoredWhenStale() {
        AppointmentProjection projection = new AppointmentProjection();
        setField(projection, "updatedAt", Instant.parse("2026-07-09T10:00:00Z"));
        UUID id = UUID.randomUUID();
        projection.applyCompleted(id, id, id, Instant.parse("2026-07-09T10:00:00Z"), Instant.parse("2026-07-09T11:00:00Z"), Instant.parse("2026-07-09T09:00:00Z"));
        assertNull(projection.getStatus());
    }

    private Instant setFieldAndGet(AppointmentProjection p, String f) {
        return (Instant) org.springframework.test.util.ReflectionTestUtils.getField(p, f);
    }
}
