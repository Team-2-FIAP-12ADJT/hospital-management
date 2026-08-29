package com.fiap.hospital.scheduling.participants.repository;

import com.fiap.hospital.scheduling.participants.domain.Doctor;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorRepository extends JpaRepository<Doctor, UUID> {
    boolean existsByTaxIdentifier(String taxIdentifier);

    boolean existsByCrm(String crm);
}
