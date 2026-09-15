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
        projection.applyScheduled(id, id, id, Instant.parse("2026-07-10T14:00:00Z"), false, null, "P", "D", "S", Instant.parse("2026-07-09T10:00:00Z"), null);
        
        assertEquals(AppointmentStatus.SCHEDULED, projection.getStatus());
        assertEquals(Instant.parse("2026-07-10T14:00:00Z"), projection.getScheduledAt());
        assertEquals(Instant.parse("2026-07-09T10:00:00Z"), setFieldAndGet(projection, "updatedAt"));
    }

    @Test
    void applyRescheduledUpdatesFields() {
        AppointmentProjection projection = new AppointmentProjection();
        setField(projection, "updatedAt", Instant.parse("2026-07-09T09:00:00Z"));
        UUID id = UUID.randomUUID();
        projection.applyRescheduled(id, id, id, Instant.parse("2026-07-10T14:00:00Z"), false, null, "P", "D", "S", Instant.parse("2026-07-09T10:00:00Z"), null);
        
        assertEquals(AppointmentStatus.SCHEDULED, projection.getStatus());
        assertEquals(Instant.parse("2026-07-10T14:00:00Z"), projection.getScheduledAt());
        assertEquals(Instant.parse("2026-07-09T10:00:00Z"), setFieldAndGet(projection, "updatedAt"));
    }

    @Test
    void applyCancelledUpdatesFields() {
        AppointmentProjection projection = new AppointmentProjection();
        setField(projection, "updatedAt", Instant.parse("2026-07-09T09:00:00Z"));
        UUID id = UUID.randomUUID();
        projection.applyCancelled(id, id, id, Instant.parse("2026-07-09T10:00:00Z"), Instant.parse("2026-07-09T11:00:00Z"), Instant.parse("2026-07-09T12:00:00Z"), null);
        
        assertEquals(AppointmentStatus.CANCELLED, projection.getStatus());
        assertEquals(Instant.parse("2026-07-09T11:00:00Z"), projection.getCancelledAt());
        assertEquals(Instant.parse("2026-07-09T12:00:00Z"), setFieldAndGet(projection, "updatedAt"));
    }

    @Test
    void applyCompletedUpdatesFields() {
        AppointmentProjection projection = new AppointmentProjection();
        setField(projection, "updatedAt", Instant.parse("2026-07-09T09:00:00Z"));
        UUID id = UUID.randomUUID();
        projection.applyCompleted(id, id, id, Instant.parse("2026-07-09T10:00:00Z"), Instant.parse("2026-07-09T11:00:00Z"), Instant.parse("2026-07-09T12:00:00Z"), null);
        
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
        afterCompleted.applyCompleted(id, id, id, Instant.parse("2026-07-09T08:00:00Z"), Instant.parse("2026-07-09T09:00:00Z"), tie, null);
        afterCompleted.applyScheduled(id, id, id, Instant.parse("2026-07-11T14:00:00Z"), false, null, "P", "D", "S", tie, null);
        afterCompleted.applyCancelled(id, id, id, Instant.parse("2026-07-11T14:00:00Z"), Instant.parse("2026-07-09T11:00:00Z"), tie, null);
        assertEquals(AppointmentStatus.COMPLETED, afterCompleted.getStatus());

        AppointmentProjection afterCancelled = new AppointmentProjection();
        afterCancelled.applyCancelled(id, id, id, Instant.parse("2026-07-09T08:00:00Z"), Instant.parse("2026-07-09T09:00:00Z"), tie, null);
        afterCancelled.applyCompleted(id, id, id, Instant.parse("2026-07-09T08:00:00Z"), Instant.parse("2026-07-09T11:00:00Z"), tie, null);
        assertEquals(AppointmentStatus.CANCELLED, afterCancelled.getStatus());
    }

    // SCHEDULED nao e terminal: o empate continua valendo como novo, que e o caso
    // que originou a guarda de empate (agendar e cancelar no mesmo milissegundo).
    @Test
    void scheduledStateStillAcceptsTiedTerminalEvent() {
        AppointmentProjection projection = new AppointmentProjection();
        UUID id = UUID.randomUUID();
        Instant tie = Instant.parse("2026-07-09T10:00:00Z");
        projection.applyScheduled(id, id, id, Instant.parse("2026-07-11T14:00:00Z"), false, null, "P", "D", "S", tie, null);

        projection.applyCancelled(id, id, id, Instant.parse("2026-07-11T14:00:00Z"), Instant.parse("2026-07-09T11:00:00Z"), tie, null);

        assertEquals(AppointmentStatus.CANCELLED, projection.getStatus());
    }

    // Reprocessar da DLT um reschedule de mesmo occurredAt que a conclusao nao pode
    // ressuscitar SCHEDULED: o completedAt ficaria preenchido numa consulta "ativa".
    @Test
    void terminalStateIsNotReopenedByTiedTimestamp() {
        AppointmentProjection projection = new AppointmentProjection();
        UUID id = UUID.randomUUID();
        Instant tie = Instant.parse("2026-07-09T10:00:00Z");
        projection.applyCompleted(id, id, id, Instant.parse("2026-07-09T08:00:00Z"), Instant.parse("2026-07-09T09:00:00Z"), tie, null);

        projection.applyRescheduled(id, id, id, Instant.parse("2026-07-11T14:00:00Z"), false, null, "P", "D", "S", tie, null);

        assertEquals(AppointmentStatus.COMPLETED, projection.getStatus());
        assertEquals(Instant.parse("2026-07-09T09:00:00Z"), projection.getCompletedAt());
    }

    @Test
    void terminalStateStillYieldsToStrictlyNewerEvent() {
        AppointmentProjection projection = new AppointmentProjection();
        UUID id = UUID.randomUUID();
        projection.applyCancelled(id, id, id, Instant.parse("2026-07-09T08:00:00Z"), Instant.parse("2026-07-09T09:00:00Z"), Instant.parse("2026-07-09T10:00:00Z"), null);

        projection.applyScheduled(id, id, id, Instant.parse("2026-07-11T14:00:00Z"), false, null, "P", "D", "S", Instant.parse("2026-07-09T10:00:00.001Z"), null);

        assertEquals(AppointmentStatus.SCHEDULED, projection.getStatus());
    }

    @Test
    void applyIsIgnoredWhenOlderThanCurrentState() {
        AppointmentProjection projection = new AppointmentProjection();
        setField(projection, "updatedAt", Instant.parse("2026-07-09T10:00:00Z"));
        UUID id = UUID.randomUUID();

        projection.applyCancelled(id, id, id, Instant.parse("2026-07-09T10:00:00Z"), Instant.parse("2026-07-09T11:00:00Z"), Instant.parse("2026-07-09T09:00:00Z"), null);

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

        projection.applyCancelled(id, id, id, Instant.parse("2026-07-09T10:00:00Z"), Instant.parse("2026-07-09T11:00:00Z"), Instant.parse("2026-07-09T10:00:00Z"), null);

        assertEquals(AppointmentStatus.CANCELLED, projection.getStatus());
        assertEquals(Instant.parse("2026-07-09T10:00:00Z"), setFieldAndGet(projection, "updatedAt"));
    }

    @Test
    void appliesScheduledAreIgnoredWhenStale() {
        AppointmentProjection projection = new AppointmentProjection();
        setField(projection, "updatedAt", Instant.parse("2026-07-09T10:00:00Z"));
        UUID id = UUID.randomUUID();
        projection.applyScheduled(id, id, id, Instant.parse("2026-07-10T14:00:00Z"), false, null, "P", "D", "S", Instant.parse("2026-07-09T09:00:00Z"), null);
        assertNull(projection.getStatus());
    }

    @Test
    void appliesRescheduledAreIgnoredWhenStale() {
        AppointmentProjection projection = new AppointmentProjection();
        setField(projection, "updatedAt", Instant.parse("2026-07-09T10:00:00Z"));
        UUID id = UUID.randomUUID();
        projection.applyRescheduled(id, id, id, Instant.parse("2026-07-10T14:00:00Z"), false, null, "P", "D", "S", Instant.parse("2026-07-09T09:00:00Z"), null);
        assertNull(projection.getStatus());
    }

    @Test
    void appliesCompletedAreIgnoredWhenStale() {
        AppointmentProjection projection = new AppointmentProjection();
        setField(projection, "updatedAt", Instant.parse("2026-07-09T10:00:00Z"));
        UUID id = UUID.randomUUID();
        projection.applyCompleted(id, id, id, Instant.parse("2026-07-09T10:00:00Z"), Instant.parse("2026-07-09T11:00:00Z"), Instant.parse("2026-07-09T09:00:00Z"), null);
        assertNull(projection.getStatus());
    }

    // aggregateVersion desempata quando os dois lados o tem, mesmo com occurredAt
    // empatado: e o cenario do replay da DLT, onde o evento reprocessado chega com
    // offset maior (portanto pareceria "mais novo" por ordem de chegada) mas carrega
    // uma versao de agregado menor que a ja aplicada.
    @Test
    void lowerAggregateVersionIsIgnoredEvenWithSameOrLaterOccurredAtAndArrival() {
        AppointmentProjection projection = new AppointmentProjection();
        UUID id = UUID.randomUUID();
        Instant tie = Instant.parse("2026-07-09T10:00:00Z");

        projection.applyScheduled(id, id, id, Instant.parse("2026-07-10T14:00:00Z"), false, null, "P", "D", "S", tie, 5L);

        // Mesmo occurredAt (empate) e aggregateVersion MENOR: nao pode reescrever.
        projection.applyCancelled(id, id, id, Instant.parse("2026-07-10T14:00:00Z"), Instant.parse("2026-07-09T11:00:00Z"), tie, 3L);

        assertEquals(AppointmentStatus.SCHEDULED, projection.getStatus());
        assertEquals(5L, projection.getAggregateVersion());

        // aggregateVersion IGUAL tambem nao reescreve (so estritamente maior vence).
        projection.applyCancelled(id, id, id, Instant.parse("2026-07-10T14:00:00Z"), Instant.parse("2026-07-09T11:00:00Z"), tie, 5L);
        assertEquals(AppointmentStatus.SCHEDULED, projection.getStatus());

        // aggregateVersion MAIOR reescreve mesmo com occurredAt anterior ao empate: a
        // versao decide, occurredAt nao e consultado quando os dois lados a tem.
        projection.applyCancelled(
                id, id, id, Instant.parse("2026-07-10T14:00:00Z"), Instant.parse("2026-07-09T11:00:00Z"),
                Instant.parse("2026-07-09T09:00:00Z"), 6L
        );
        assertEquals(AppointmentStatus.CANCELLED, projection.getStatus());
        assertEquals(6L, projection.getAggregateVersion());
    }

    // Linha ja tem aggregateVersion (de um evento anterior versionado); o evento novo
    // chega sem o campo (replay de antes da mudanca, ou agregado sem versao). Falta em
    // um dos lados tem de cair na regra antiga de occurredAt, nao travar a projecao.
    @Test
    void fallsBackToOccurredAtRuleWhenOnlyExistingRowHasAggregateVersion() {
        AppointmentProjection projection = new AppointmentProjection();
        UUID id = UUID.randomUUID();

        projection.applyScheduled(
                id, id, id, Instant.parse("2026-07-10T14:00:00Z"), false, null, "P", "D", "S",
                Instant.parse("2026-07-09T10:00:00Z"), 5L
        );

        projection.applyCancelled(
                id, id, id, Instant.parse("2026-07-10T14:00:00Z"), Instant.parse("2026-07-09T11:00:00Z"),
                Instant.parse("2026-07-09T10:00:00.001Z"), null
        );

        assertEquals(AppointmentStatus.CANCELLED, projection.getStatus());
    }

    // Sem aggregateVersion em nenhum dos dois lados (evento de antes da mudanca, ou
    // replay desde o offset zero), a projecao tem de continuar reconstruivel pela
    // regra antiga de occurredAt — a guarda de aggregateVersion nao pode interferir.
    @Test
    void applyStillUsesOccurredAtRuleWhenAggregateVersionIsAbsent() {
        AppointmentProjection projection = new AppointmentProjection();
        UUID id = UUID.randomUUID();

        projection.applyScheduled(
                id, id, id, Instant.parse("2026-07-10T14:00:00Z"), false, null, "P", "D", "S",
                Instant.parse("2026-07-09T10:00:00Z"), null
        );
        projection.applyCancelled(
                id, id, id, Instant.parse("2026-07-10T14:00:00Z"), Instant.parse("2026-07-09T11:00:00Z"),
                Instant.parse("2026-07-09T10:00:00.001Z"), null
        );

        assertEquals(AppointmentStatus.CANCELLED, projection.getStatus());
    }

    private Instant setFieldAndGet(AppointmentProjection p, String f) {
        return (Instant) org.springframework.test.util.ReflectionTestUtils.getField(p, f);
    }
}
