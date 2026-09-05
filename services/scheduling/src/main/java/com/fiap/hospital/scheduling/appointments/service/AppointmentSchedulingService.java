package com.fiap.hospital.scheduling.appointments.service;

import com.fiap.hospital.scheduling.appointments.domain.Appointment;
import com.fiap.hospital.scheduling.appointments.repository.AppointmentRepository;
import java.time.Clock;
import java.time.Instant;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AppointmentSchedulingService {

    private final AppointmentRepository appointmentRepository;
    private final Clock clock;

    public AppointmentSchedulingService(AppointmentRepository appointmentRepository, Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.clock = clock;
    }

    @Transactional
    public Appointment schedule(
        UUID patientId, UUID doctorId, Instant scheduledAt, boolean fitIn, String fitInReason
    ) {
        appointmentRepository.acquireDoctorScheduleLock(doctorId);
        Instant now = clock.instant();
        Instant normalizedScheduledAt = Appointment.normalizeInstant(scheduledAt);
        boolean occupied = appointmentRepository.isOccupied(doctorId, normalizedScheduledAt, null);
        Appointment appointment = Appointment.schedule(
            UUID.randomUUID(), patientId, doctorId, normalizedScheduledAt, fitIn, fitInReason, now, occupied
        );
        try {
            return appointmentRepository.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException ex) {
            throw translateForeignKey(ex);
        }
    }

    @Transactional
    public Appointment reschedule(
        UUID id, Instant scheduledAt, boolean fitIn, String fitInReason
    ) {
        UUID doctorId = appointmentRepository.findDoctorIdById(id);
        if (doctorId == null) {
            throw notFound();
        }
        appointmentRepository.acquireDoctorScheduleLock(doctorId);
        Appointment appointment = appointmentRepository.findByIdForUpdate(id);
        if (appointment == null) {
            throw notFound();
        }
        Instant now = clock.instant();
        Instant normalizedScheduledAt = Appointment.normalizeInstant(scheduledAt);
        boolean occupied = appointmentRepository.isOccupied(doctorId, normalizedScheduledAt, id);
        appointment.reschedule(normalizedScheduledAt, fitIn, fitInReason, now, occupied);
        return appointmentRepository.saveAndFlush(appointment);
    }

    @Transactional
    public void cancel(UUID id) {
        Appointment appointment = locked(id);
        appointment.cancel(clock.instant());
        appointmentRepository.saveAndFlush(appointment);
    }

    @Transactional
    public boolean complete(UUID id) {
        Appointment appointment = locked(id);
        boolean changed = appointment.complete();
        if (changed) {
            appointmentRepository.saveAndFlush(appointment);
        }
        return changed;
    }

    private Appointment locked(UUID id) {
        Appointment appointment = appointmentRepository.findByIdForUpdate(id);
        if (appointment == null) {
            throw notFound();
        }
        return appointment;
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "appointment not found");
    }

    // SQLSTATE 23503 e violacao de FK, mas sozinho nao identifica a origem: saveAndFlush
    // descarrega o contexto de persistencia inteiro, entao numa transacao externa a
    // violacao pode vir de outra entidade. O nome da constraint vem do
    // ConstraintViolationException do Hibernate, que ja e dependencia do modulo — nao do
    // texto da mensagem, que depende de idioma e formato, nem de PSQLException, que
    // acoplaria a producao ao driver.
    private static RuntimeException translateForeignKey(DataIntegrityViolationException ex) {
        String constraint = constraintName(ex);
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof SQLException sqlException
                && "23503".equals(sqlException.getSQLState())
                && isParticipantForeignKey(constraint)) {
                return new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "participant does not exist", ex
                );
            }
            cause = cause.getCause();
        }
        throw ex;
    }

    private static String constraintName(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation) {
                return violation.getConstraintName();
            }
            cause = cause.getCause();
        }
        return null;
    }

    private static boolean isParticipantForeignKey(String constraint) {
        return "appointment_patient_id_fkey".equals(constraint)
            || "appointment_doctor_id_fkey".equals(constraint);
    }

}
