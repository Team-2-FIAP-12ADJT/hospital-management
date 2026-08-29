package com.fiap.hospital.scheduling.participants.repository;

import com.fiap.hospital.scheduling.participants.domain.Patient;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientRepository extends JpaRepository<Patient, UUID> {
    boolean existsByTaxIdentifier(String taxIdentifier);
}
