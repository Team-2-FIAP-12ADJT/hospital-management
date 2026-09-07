package com.fiap.hospital.scheduling.appointments.repository;

import com.fiap.hospital.scheduling.appointments.domain.Appointment;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    @Query(value = "SELECT pg_advisory_xact_lock(hashtextextended(CAST(:doctorId AS text), 0))", nativeQuery = true)
    void acquireDoctorScheduleLock(UUID doctorId);

    @Query("select a.doctorId from Appointment a where a.id = :id")
    UUID findDoctorIdById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Appointment a where a.id = :id")
    Appointment findByIdForUpdate(UUID id);

    @Query(value = """
        SELECT EXISTS (
            SELECT 1 FROM scheduling.appointment a
            WHERE a.doctor_id = :doctorId
              AND a.scheduled_at = :scheduledAt
              AND a.status <> 'CANCELLED'
              AND (:excludedId IS NULL OR a.id <> :excludedId)
        )
        """, nativeQuery = true)
    boolean isOccupied(UUID doctorId, java.time.Instant scheduledAt, UUID excludedId);
}
