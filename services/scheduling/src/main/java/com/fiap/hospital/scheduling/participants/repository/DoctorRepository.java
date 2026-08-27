package com.fiap.hospital.scheduling.participants.repository;

import com.fiap.hospital.scheduling.participants.domain.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DoctorRepository extends JpaRepository<Doctor, UUID> {

    boolean existsByTaxIdentifier(String taxIdentifier);

    boolean existsByCrm(String crm);
}
