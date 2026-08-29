package com.fiap.hospital.scheduling.participants.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fiap.hospital.scheduling.outbox.OutboxEventWriter;
import com.fiap.hospital.scheduling.participants.domain.Patient;
import com.fiap.hospital.scheduling.participants.repository.PatientRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PatientRegistrationServiceConflictTest {

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private OutboxEventWriter outboxEventWriter;

    @Test
    void uniqueViolationOnFlushBecomesConflictAndSkipsOutbox() {
        PatientRegistrationService service = new PatientRegistrationService(
            patientRepository,
            outboxEventWriter
        );

        when(patientRepository.existsByTaxIdentifier("39053344705")).thenReturn(
            false
        );
        when(patientRepository.saveAndFlush(any(Patient.class))).thenThrow(
            new DataIntegrityViolationException("uk_patient_tax_identifier")
        );

        assertThatThrownBy(() ->
            service.register(
                "39053344705",
                "Maria Souza",
                "maria.souza@hospital.local",
                "+5511999999999"
            )
        )
            .isInstanceOf(ResponseStatusException.class)
            .extracting("statusCode")
            .isEqualTo(HttpStatus.CONFLICT);

        verifyNoInteractions(outboxEventWriter);
    }

    @Test
    void integrityViolationOutsideThePatientInsertIsNotTranslatedIntoConflict() {
        PatientRegistrationService service = new PatientRegistrationService(
            patientRepository,
            outboxEventWriter
        );

        when(patientRepository.existsByTaxIdentifier("39053344705")).thenReturn(
            false
        );
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
                "Maria Souza",
                "maria.souza@hospital.local",
                "+5511999999999"
            )
        )
            .isInstanceOf(DataIntegrityViolationException.class)
            .isNotInstanceOf(ResponseStatusException.class);
    }
}
