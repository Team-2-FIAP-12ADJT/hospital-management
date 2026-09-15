package com.fiap.hospital.scheduling.appointments.service;

import com.fiap.hospital.scheduling.appointments.domain.Appointment;
import com.fiap.hospital.scheduling.appointments.repository.AppointmentRepository;
import com.fiap.hospital.scheduling.outbox.Aggregate;
import com.fiap.hospital.scheduling.outbox.OutboxEventWriter;
import com.fiap.hospital.scheduling.participants.contract.DoctorSummary;
import com.fiap.hospital.scheduling.participants.contract.ParticipantDirectory;
import com.fiap.hospital.scheduling.participants.contract.PatientSummary;
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

    private static final String SCHEDULED_EVENT_TYPE = "AppointmentScheduled";
    private static final String RESCHEDULED_EVENT_TYPE = "AppointmentRescheduled";
    private static final String CANCELLED_EVENT_TYPE = "AppointmentCancelled";
    private static final String COMPLETED_EVENT_TYPE = "AppointmentCompleted";
    private static final int EVENT_VERSION = 1;

    private final AppointmentRepository appointmentRepository;
    private final ParticipantDirectory participantDirectory;
    private final OutboxEventWriter outboxEventWriter;
    private final Clock clock;

    public AppointmentSchedulingService(
        AppointmentRepository appointmentRepository,
        ParticipantDirectory participantDirectory,
        OutboxEventWriter outboxEventWriter,
        Clock clock
    ) {
        this.appointmentRepository = appointmentRepository;
        this.participantDirectory = participantDirectory;
        this.outboxEventWriter = outboxEventWriter;
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
            appointmentRepository.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException ex) {
            throw translateForeignKey(ex);
        }
        // A FK e a autoridade sobre existencia; so depois do flush o evento pode nascer.
        PatientSummary patient = participantDirectory.patient(appointment.getPatientId());
        DoctorSummary doctor = participantDirectory.doctor(appointment.getDoctorId());
        AppointmentScheduledEvent event = new AppointmentScheduledEvent(
            appointment.getId(),
            appointment.getPatientId(),
            appointment.getDoctorId(),
            appointment.getScheduledAt(),
            appointment.getStatus().name(),
            appointment.isFitIn(),
            appointment.getFitInReason(),
            patient.name(),
            doctor.name(),
            doctor.specialty()
        );
        publish(appointment.getId(), SCHEDULED_EVENT_TYPE, now, event);
        return appointment;
    }

    private void publish(UUID appointmentId, String eventType, Instant occurredAt, Object data) {
        outboxEventWriter.append(
            Aggregate.APPOINTMENT, appointmentId, eventType, EVENT_VERSION, occurredAt, data
        );
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
        // Lido antes da transicao: e o campo que permite ao consumidor trocar o lembrete.
        Instant previousScheduledAt = appointment.getScheduledAt();
        appointment.reschedule(normalizedScheduledAt, fitIn, fitInReason, now, occupied);
        Appointment saved = appointmentRepository.saveAndFlush(appointment);

        PatientSummary patient = participantDirectory.patient(saved.getPatientId());
        DoctorSummary doctor = participantDirectory.doctor(saved.getDoctorId());
        publish(saved.getId(), RESCHEDULED_EVENT_TYPE, now, new AppointmentRescheduledEvent(
            saved.getId(),
            saved.getPatientId(),
            saved.getDoctorId(),
            previousScheduledAt,
            saved.getScheduledAt(),
            saved.getStatus().name(),
            saved.isFitIn(),
            saved.getFitInReason(),
            patient.name(),
            doctor.name(),
            doctor.specialty()
        ));
        return saved;
    }

    @Transactional
    public void cancel(UUID id) {
        Appointment appointment = locked(id);
        Instant now = clock.instant();
        appointment.cancel(now);
        appointmentRepository.saveAndFlush(appointment);

        publish(appointment.getId(), CANCELLED_EVENT_TYPE, now, new AppointmentCancelledEvent(
            appointment.getId(),
            appointment.getPatientId(),
            appointment.getDoctorId(),
            appointment.getScheduledAt(),
            appointment.getStatus().name(),
            appointment.getCancelledAt()
        ));
    }

    @Transactional
    public boolean complete(UUID id) {
        Appointment appointment = locked(id);
        Instant now = clock.instant();
        boolean changed = appointment.complete(now);
        if (!changed) {
            return false;
        }
        appointmentRepository.saveAndFlush(appointment);

        publish(appointment.getId(), COMPLETED_EVENT_TYPE, now, new AppointmentCompletedEvent(
            appointment.getId(),
            appointment.getPatientId(),
            appointment.getDoctorId(),
            appointment.getScheduledAt(),
            appointment.getStatus().name(),
            appointment.getCompletedAt()
        ));
        return true;
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
