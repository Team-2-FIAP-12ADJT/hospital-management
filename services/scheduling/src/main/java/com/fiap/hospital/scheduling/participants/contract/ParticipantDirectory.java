package com.fiap.hospital.scheduling.participants.contract;

import com.fiap.hospital.scheduling.participants.repository.DoctorRepository;
import com.fiap.hospital.scheduling.participants.repository.PatientRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ParticipantDirectory {
    // Este pacote e o ponto de passagem porque appointments so pode alcancar participants
    // por domain ou contract, regra verificada pelo DependencyRuleTest.

    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;

    public ParticipantDirectory(
        PatientRepository patientRepository,
        DoctorRepository doctorRepository
    ) {
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
    }

    public PatientSummary patient(UUID id) {
        // A FK ja provou a existencia antes desta chamada; ausencia indica bug, nao 404.
        return patientRepository.findById(id)
            .map(patient -> new PatientSummary(patient.getId(), patient.getName()))
            .orElseThrow(() -> new IllegalStateException("patient not found"));
    }

    public DoctorSummary doctor(UUID id) {
        // A FK ja provou a existencia antes desta chamada; ausencia indica bug, nao 404.
        return doctorRepository.findById(id)
            .map(doctor -> new DoctorSummary(
                doctor.getId(), doctor.getName(), doctor.getSpecialty()
            ))
            .orElseThrow(() -> new IllegalStateException("doctor not found"));
    }
}
