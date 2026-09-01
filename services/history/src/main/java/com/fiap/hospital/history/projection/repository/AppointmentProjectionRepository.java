package com.fiap.hospital.history.projection.repository;

import com.fiap.hospital.history.projection.domain.AppointmentProjection;
import com.fiap.hospital.history.projection.domain.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface AppointmentProjectionRepository extends JpaRepository<AppointmentProjection, UUID> {

    Page<AppointmentProjection> findByPatientId(UUID patientId, Pageable pageable);

    Page<AppointmentProjection> findByPatientIdAndStatusAndScheduledAtGreaterThanEqual(
            UUID patientId,
            AppointmentStatus status,
            Instant scheduledAt,
            Pageable pageable
    );
}
