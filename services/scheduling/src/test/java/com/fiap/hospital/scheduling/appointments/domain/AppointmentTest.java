package com.fiap.hospital.scheduling.appointments.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AppointmentTest {

    private static final Instant NOW = Instant.parse("2026-09-05T22:00:00.123456789Z");
    private static final Instant FUTURE = Instant.parse("2026-09-05T23:00:00.999999999Z");

    @Test
    void schedule_normalizes_time_and_starts_scheduled() {
        Appointment appointment = schedule(false, null, false);

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
        assertThat(appointment.getScheduledAt())
            .isEqualTo(Instant.parse("2026-09-05T23:00:00.999Z"));
    }

    @Test
    void schedule_rejects_past_and_occupied_normal_appointment() {
        assertThatThrownBy(() -> Appointment.schedule(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            NOW.minusMillis(1), false, null, NOW, false
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> schedule(false, null, true))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fit_in_requires_non_blank_reason_with_maximum_length() {
        assertThatThrownBy(() -> schedule(true, " ", false))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> schedule(true, "x".repeat(256), false))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(schedule(true, "urgent case", true).isFitIn()).isTrue();
        assertThatThrownBy(() -> schedule(false, "reason", false))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalid_reschedule_reason_does_not_mutate_the_appointment() {
        Appointment appointment = schedule(true, "original reason", false);
        Instant originalTime = appointment.getScheduledAt();

        assertThatThrownBy(() ->
            appointment.reschedule(FUTURE.plusSeconds(1), true, " ", NOW, false)
        ).isInstanceOf(IllegalArgumentException.class);

        assertThat(appointment.getScheduledAt()).isEqualTo(originalTime);
        assertThat(appointment.isFitIn()).isTrue();
        assertThat(appointment.getFitInReason()).isEqualTo("original reason");
    }

    @Test
    void lifecycle_enforces_time_and_state_rules_and_completion_is_idempotent() {
        Appointment appointment = schedule(false, null, false);

        appointment.reschedule(FUTURE.plusSeconds(1), false, null, NOW, false);
        appointment.cancel(NOW);
        assertThat(appointment.getCancelledAt())
            .isEqualTo(Instant.parse("2026-09-05T22:00:00.123Z"));
        assertThatThrownBy(() -> appointment.reschedule(FUTURE, false, null, NOW, false))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> appointment.complete(NOW))
            .isInstanceOf(IllegalStateException.class);

        Appointment completable = schedule(false, null, false);
        assertThat(completable.complete(NOW)).isTrue();
        assertThat(completable.getCompletedAt())
            .isEqualTo(Instant.parse("2026-09-05T22:00:00.123Z"));
        assertThat(completable.complete(NOW.plusSeconds(60)))
            .as("conclusao repetida nao muda nada e nao publica segundo evento")
            .isFalse();
        assertThat(completable.getCompletedAt())
            .as("o instante da conclusao e o da primeira, nao o da repeticao")
            .isEqualTo(Instant.parse("2026-09-05T22:00:00.123Z"));
        assertThat(completable.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
    }

    @Test
    void lifecycle_rejects_started_and_past_operations_but_allows_minimum_future_time() {
        Appointment atBoundary = Appointment.schedule(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            NOW, false, null, NOW, false
        );
        assertThatThrownBy(() -> atBoundary.cancel(NOW))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() ->
            atBoundary.reschedule(FUTURE, false, null, NOW, false)
        ).isInstanceOf(IllegalStateException.class);

        Appointment past = Appointment.schedule(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            NOW.minusSeconds(1), false, null, NOW.minusSeconds(2), false
        );
        assertThatThrownBy(() -> past.cancel(NOW))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() ->
            past.reschedule(FUTURE, false, null, NOW, false)
        ).isInstanceOf(IllegalStateException.class);

        Appointment completed = schedule(false, null, false);
        completed.complete(NOW);
        assertThatThrownBy(() -> completed.cancel(NOW))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() ->
            completed.reschedule(FUTURE, false, null, NOW, false)
        ).isInstanceOf(IllegalStateException.class);

        Appointment minimumFuture = Appointment.schedule(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            NOW.plusMillis(1), false, null, NOW, false
        );
        assertThat(minimumFuture.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);

        // Cancelar na iminencia do inicio e permitido (ADR-0010: nao ha janela minima):
        // um milissegundo antes a consulta ainda nao comecou.
        minimumFuture.cancel(NOW);
        assertThat(minimumFuture.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);

        // Remarcar para o proximo milissegundo tambem: nao ha antecedencia minima.
        Appointment nextMillisecond = schedule(false, null, false);
        nextMillisecond.reschedule(NOW.plusMillis(1), false, null, NOW, false);
        assertThat(nextMillisecond.getScheduledAt())
            .isEqualTo(Instant.parse("2026-09-05T22:00:00.124Z"));
    }

    @Test
    void reschedule_rejects_existing_appointment_that_started_or_passed() {
        Appointment appointment = Appointment.schedule(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            NOW, false, null, NOW.minusSeconds(1), false
        );

        assertThatThrownBy(() ->
            appointment.reschedule(FUTURE, false, null, NOW, false)
        ).isInstanceOf(IllegalStateException.class);
    }

    // Destino no passado ou igual a now, com a consulta ORIGINAL ainda futura: e o unico
    // arranjo que isola a validacao do novo horario, porque a consulta nao e recusada antes
    // pela regra de "ja iniciada".
    @Test
    void reschedule_rejects_past_or_current_destination_and_preserves_aggregate() {
        Appointment appointment = schedule(false, null, false);

        assertThatThrownBy(() ->
            appointment.reschedule(NOW.minusSeconds(1), false, null, NOW, false)
        ).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
            appointment.reschedule(NOW, false, null, NOW, false)
        ).isInstanceOf(IllegalArgumentException.class);

        assertThat(appointment.getScheduledAt())
            .isEqualTo(Instant.parse("2026-09-05T23:00:00.999Z"));
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
        assertThat(appointment.isFitIn()).isFalse();
        assertThat(appointment.getFitInReason()).isNull();
    }

    private static Appointment schedule(boolean fitIn, String reason, boolean occupied) {
        return Appointment.schedule(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            FUTURE,
            fitIn,
            reason,
            NOW,
            occupied
        );
    }
}
