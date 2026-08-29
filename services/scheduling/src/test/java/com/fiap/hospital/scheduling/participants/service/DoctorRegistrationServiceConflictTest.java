package com.fiap.hospital.scheduling.participants.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fiap.hospital.scheduling.outbox.OutboxEventWriter;
import com.fiap.hospital.scheduling.participants.domain.Doctor;
import com.fiap.hospital.scheduling.participants.repository.DoctorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

// A corrida entre o pre-check e o flush nao e reproduzivel de forma deterministica
// contra o banco: duas threads que serializam caem no pre-check e o catch nunca roda.
// Aqui o repositorio e simulado para que o flush falhe com a violacao de UNIQUE que
// so a corrida real produziria, e o teste prova o que sobra dessa rota: sai 409, e o
// evento nao vai para o outbox.
@ExtendWith(MockitoExtension.class)
class DoctorRegistrationServiceConflictTest {

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private OutboxEventWriter outboxEventWriter;

    @Test
    void uniqueViolationOnFlushBecomesConflictAndSkipsOutbox() {
        DoctorRegistrationService service = new DoctorRegistrationService(
            doctorRepository,
            outboxEventWriter
        );

        when(doctorRepository.existsByTaxIdentifier("39053344705")).thenReturn(
            false
        );
        when(doctorRepository.existsByCrm("CRM-SP 123456")).thenReturn(false);
        when(doctorRepository.saveAndFlush(any(Doctor.class))).thenThrow(
            new DataIntegrityViolationException("uk_doctor_tax_identifier")
        );

        assertThatThrownBy(() ->
            service.register(
                "39053344705",
                "CRM-SP 123456",
                "Cardiologia",
                "Dra. Helena Prado",
                "helena.prado@hospital.local"
            )
        )
            .isInstanceOf(ResponseStatusException.class)
            .extracting("statusCode")
            .isEqualTo(HttpStatus.CONFLICT);

        verifyNoInteractions(outboxEventWriter);
    }

    // O defeito que o ApiExceptionHandler tinha era de alcance: sendo
    // @RestControllerAdvice, ele traduzia qualquer DataIntegrityViolationException do
    // modulo inteiro em 409, inclusive as de FK e NOT NULL. Aqui a traducao envolve so
    // o saveAndFlush do Doctor: uma violacao levantada depois dele, ja na escrita do
    // outbox, tem de propagar como esta e virar 500.
    @Test
    void integrityViolationOutsideTheDoctorInsertIsNotTranslatedIntoConflict() {
        DoctorRegistrationService service = new DoctorRegistrationService(
            doctorRepository,
            outboxEventWriter
        );

        when(doctorRepository.existsByTaxIdentifier("39053344705")).thenReturn(
            false
        );
        when(doctorRepository.existsByCrm("CRM-SP 123456")).thenReturn(false);
        when(
            outboxEventWriter.append(
                any(),
                any(),
                any(),
                anyInt(),
                any(),
                any()
            )
        ).thenThrow(
            new DataIntegrityViolationException("outbox_events_topic_not_null")
        );

        assertThatThrownBy(() ->
            service.register(
                "39053344705",
                "CRM-SP 123456",
                "Cardiologia",
                "Dra. Helena Prado",
                "helena.prado@hospital.local"
            )
        )
            .isInstanceOf(DataIntegrityViolationException.class)
            .isNotInstanceOf(ResponseStatusException.class);
    }
}
