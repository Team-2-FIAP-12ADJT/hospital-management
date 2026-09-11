package com.fiap.hospital.history.projection.consumer;

import com.fiap.hospital.history.projection.domain.AppointmentProjection;
import com.fiap.hospital.history.projection.repository.AppointmentProjectionRepository;
import com.fiap.hospital.history.projection.repository.ProjectionFreshnessRepository;
import org.springframework.stereotype.Service;

@Service
public class ApplyAppointmentScheduled {

    private final AppointmentProjectionRepository appointments;
    private final ProjectionFreshnessRepository freshness;
    private final IdempotencyService idempotency;

    public ApplyAppointmentScheduled(
            AppointmentProjectionRepository appointments,
            ProjectionFreshnessRepository freshness,
            IdempotencyService idempotency
    ) {
        this.appointments = appointments;
        this.freshness = freshness;
        this.idempotency = idempotency;
    }

    public void apply(AppointmentScheduledMessage message) {
        idempotency.process(message.eventId(), () -> persist(message));
    }

    private void persist(AppointmentScheduledMessage message) {
        AppointmentProjection row = appointments.findById(message.appointmentId())
                .orElseGet(AppointmentProjection::new);
        row.applyScheduled(
                message.appointmentId(),
                message.patientId(),
                message.doctorId(),
                message.scheduledAt(),
                message.fitIn(),
                message.fitInReason(),
                message.patientName(),
                message.doctorName(),
                message.doctorSpecialty(),
                message.occurredAt()
        );
        appointments.save(row);
        freshness.markApplied(message.occurredAt());
    }
}
