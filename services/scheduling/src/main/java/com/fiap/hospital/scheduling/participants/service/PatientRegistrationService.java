package com.fiap.hospital.scheduling.participants.service;

import com.fiap.hospital.scheduling.outbox.Aggregate;
import com.fiap.hospital.scheduling.outbox.OutboxEventWriter;
import com.fiap.hospital.scheduling.participants.domain.Patient;
import com.fiap.hospital.scheduling.participants.repository.PatientRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PatientRegistrationService {

    private static final String EVENT_TYPE = "PatientRegistered";
    private static final int EVENT_VERSION = 1;
    private static final String ROLE = "PATIENT";

    private final PatientRepository patientRepository;
    private final OutboxEventWriter outboxEventWriter;

    public PatientRegistrationService(
        PatientRepository patientRepository,
        OutboxEventWriter outboxEventWriter
    ) {
        this.patientRepository = patientRepository;
        this.outboxEventWriter = outboxEventWriter;
    }

    // Grava Patient e o outbox na mesma transação (ADR-0012): ou os dois existem, ou nenhum.
    @Transactional
    public Patient register(
        String taxIdentifier,
        String name,
        String email,
        String phone
    ) {
        if (patientRepository.existsByTaxIdentifier(taxIdentifier)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "tax identifier already registered"
            );
        }

        Patient patient = new Patient(
            UUID.randomUUID(),
            taxIdentifier,
            name,
            email,
            phone
        );
        try {
            patientRepository.saveAndFlush(patient);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "tax identifier already registered",
                ex
            );
        }

        PatientRegisteredEvent event = new PatientRegisteredEvent(
            patient.getId(),
            taxIdentifier,
            name,
            email,
            phone,
            ROLE
        );
        outboxEventWriter.append(
            Aggregate.PERSON,
            patient.getId(),
            EVENT_TYPE,
            EVENT_VERSION,
            Instant.now(),
            event
        );

        return patient;
    }
}
