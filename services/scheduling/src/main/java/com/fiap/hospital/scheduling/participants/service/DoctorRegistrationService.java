package com.fiap.hospital.scheduling.participants.service;

import com.fiap.hospital.scheduling.outbox.Aggregate;
import com.fiap.hospital.scheduling.outbox.OutboxEventWriter;
import com.fiap.hospital.scheduling.participants.domain.Doctor;
import com.fiap.hospital.scheduling.participants.repository.DoctorRepository;
import com.fiap.hospital.scheduling.participants.repository.PatientRepository;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final PatientRepository patientRepository;
    private final OutboxEventWriter outboxEventWriter;

    public DoctorRegistrationService(
            DoctorRepository doctorRepository,
            PatientRepository patientRepository,
            OutboxEventWriter outboxEventWriter) {
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.outboxEventWriter = outboxEventWriter;
    }

    // Grava Doctor e o outbox na mesma transação (ADR-0012): ou os dois existem, ou nenhum.
    @Transactional
    public Doctor register(String taxIdentifier, String crm, String specialty, String name, String email) {
        // O par pre-check + catch e deliberado. O pre-check e o caminho comum: sem ele
        // toda tentativa duplicada chega ao INSERT e o Hibernate registra o CPF em WARN,
        // o que poe PII no log e deixa a rota autenticada inundar o log por repeticao.
        // O catch cobre a corrida entre o pre-check e o flush, que so a UNIQUE resolve.
        if (doctorRepository.existsByTaxIdentifier(taxIdentifier)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "tax identifier already registered");
        }
        // A checagem cruzada nao tem UNIQUE por tras: doctor e patient sao tabelas
        // distintas e nao existe constraint que atravesse as duas, entao aqui o pre-check
        // e a defesa inteira. A corrida entre ele e o commit fica descoberta: dois
        // cadastros simultaneos do mesmo CPF, um como medico e outro como paciente,
        // ainda passam. Nesse caso o identity descarta o segundo evento (uma conta por
        // CPF) e sobra um participante orfao — e essa a ultima linha que restou.
        // Fechar a corrida de verdade exigiria promover pessoa a agregado proprio, que
        // e exatamente o que o ADR-0015 recusou.
        if (patientRepository.existsByTaxIdentifier(taxIdentifier)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "tax identifier already registered as patient");
        }
        if (doctorRepository.existsByCrm(crm)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "crm already registered");
        }

        Doctor doctor = new Doctor(UUID.randomUUID(), taxIdentifier, crm, specialty, name, email);
        try {
            doctorRepository.saveAndFlush(doctor);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "tax identifier or crm already registered", ex);
        }

        DoctorRegisteredEvent event = new DoctorRegisteredEvent(
                doctor.getId(), taxIdentifier, crm, specialty, name, email, ROLE);
        outboxEventWriter.append(
                Aggregate.PERSON, doctor.getId(), EVENT_TYPE, EVENT_VERSION, null, Instant.now(), event);

        return doctor;
    }
}
