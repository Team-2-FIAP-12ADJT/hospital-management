package com.fiap.hospital.history.projection.repository;

import com.fiap.hospital.history.projection.domain.AppointmentProjection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AppointmentProjectionRepository extends JpaRepository<AppointmentProjection, UUID> {

    List<AppointmentProjection> findAllByOrderByScheduledAtDesc();

    List<AppointmentProjection> findByPatientIdOrderByScheduledAtDesc(UUID patientId);
}
