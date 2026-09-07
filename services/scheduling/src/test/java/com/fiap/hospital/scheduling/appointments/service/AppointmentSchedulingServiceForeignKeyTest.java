package com.fiap.hospital.scheduling.appointments.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fiap.hospital.scheduling.appointments.repository.AppointmentRepository;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

// Traducao de violacao de integridade: so a FK de participante vira 400. O nome da
// constraint importa porque saveAndFlush descarrega o contexto inteiro — um 23503 pode
// nascer de outra entidade quando o servico roda dentro de transacao externa.
class AppointmentSchedulingServiceForeignKeyTest {

    @Test
    void participant_foreign_key_violation_becomes_bad_request() {
        Throwable thrown = catchThrowable(() ->
            scheduleFailingWith("appointment_doctor_id_fkey", "23503"));

        assertThat(thrown).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) thrown).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknown_foreign_key_violation_is_not_translated_to_bad_request() {
        assertThatThrownBy(() -> scheduleFailingWith("outbox_event_aggregate_fkey", "23503"))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessage("integrity");
    }

    // Constraint CONHECIDA com SQLSTATE de unicidade: e o unico arranjo em que so a
    // checagem de SQLSTATE pode recusar a traducao. Com constraint desconhecida o teste
    // passaria mesmo sem essa checagem, e nao provaria nada sobre ela.
    @Test
    void non_foreign_key_integrity_violation_is_not_translated_to_bad_request() {
        assertThatThrownBy(() -> scheduleFailingWith("appointment_doctor_id_fkey", "23505"))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessage("integrity");
    }

    private void scheduleFailingWith(String constraint, String sqlState) {
        AppointmentRepository repository = mock(AppointmentRepository.class);
        when(repository.isOccupied(any(), any(), isNull())).thenReturn(false);

        SQLException databaseException = new SQLException("violation", sqlState);
        ConstraintViolationException hibernateException = new ConstraintViolationException(
            "violation", databaseException, constraint
        );
        when(repository.saveAndFlush(any()))
            .thenThrow(new DataIntegrityViolationException("integrity", hibernateException));

        AppointmentSchedulingService service = new AppointmentSchedulingService(
            repository,
            Clock.fixed(Instant.parse("2030-01-01T12:00:00Z"), ZoneOffset.UTC)
        );

        service.schedule(
            UUID.randomUUID(), UUID.randomUUID(),
            Instant.parse("2030-01-01T13:00:00Z"), false, null
        );
    }
}
