package com.fiap.hospital.scheduling.participants.service;

import com.fiap.hospital.scheduling.outbox.Aggregate;
import com.fiap.hospital.scheduling.outbox.OutboxEventWriter;
import com.fiap.hospital.scheduling.participants.domain.Doctor;
import com.fiap.hospital.scheduling.participants.repository.DoctorRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Service
public class DoctorRegistrationService {

    private static final String EVENT_TYPE = "DoctorRegistered";
    private static final int EVENT_VERSION = 1;
    private static final String ROLE = "DOCTOR";

    private final DoctorRepository doctorRepository;
    private final OutboxEventWriter outboxEventWriter;

    public DoctorRegistrationService(DoctorRepository doctorRepository, OutboxEventWriter outboxEventWriter) {
        this.doctorRepository = doctorRepository;
        this.outboxEventWriter = outboxEventWriter;
    }

    // Grava Doctor e o outbox na mesma transação (ADR-0012): ou os dois existem, ou nenhum.
    @Transactional
    public Doctor register(String taxIdentifier, String crm, String specialty, String name, String email) {
        if (doctorRepository.existsByTaxIdentifier(taxIdentifier)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "tax identifier already registered");
        }
        if (doctorRepository.existsByCrm(crm)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "crm already registered");
        }

        Doctor doctor = new Doctor(UUID.randomUUID(), taxIdentifier, crm, specialty, name, email);
        doctorRepository.save(doctor);

        DoctorRegisteredEvent event = new DoctorRegisteredEvent(
                doctor.getId(), taxIdentifier, crm, specialty, name, email, ROLE);
        outboxEventWriter.append(Aggregate.PERSON, doctor.getId(), EVENT_TYPE, EVENT_VERSION, Instant.now(), event);

        return doctor;
    }
}
