package com.fiap.hospital.history.projection.consumer;

import com.fiap.hospital.history.projection.domain.AppointmentProjection;
import com.fiap.hospital.history.projection.repository.AppointmentProjectionRepository;
import com.fiap.hospital.history.projection.repository.ProjectionFreshnessRepository;
import org.springframework.stereotype.Service;

@Service
public class ApplyAppointmentEvent {

    private final AppointmentProjectionRepository appointments;
    private final ProjectionFreshnessRepository freshness;
    private final IdempotencyService idempotency;

    public ApplyAppointmentEvent(
            AppointmentProjectionRepository appointments,
            ProjectionFreshnessRepository freshness,
            IdempotencyService idempotency
    ) {
        this.appointments = appointments;
        this.freshness = freshness;
        this.idempotency = idempotency;
    }

    public void apply(AppointmentEvent event) {
        idempotency.process(event.eventId(), () -> persist(event));
    }

    private void persist(AppointmentEvent event) {
        AppointmentProjection row = appointments.findById(event.appointmentId())
                .orElseGet(AppointmentProjection::new);

        switch (event) {
            case AppointmentScheduledMessage m -> row.applyScheduled(
                    m.appointmentId(), m.patientId(), m.doctorId(),
                    m.scheduledAt(), m.fitIn(), m.fitInReason(),
                    m.patientName(), m.doctorName(), m.doctorSpecialty(),
                    m.occurredAt(), m.aggregateVersion()
            );
            case AppointmentRescheduledMessage m -> row.applyRescheduled(
                    m.appointmentId(), m.patientId(), m.doctorId(),
                    m.scheduledAt(), m.fitIn(), m.fitInReason(),
                    m.patientName(), m.doctorName(), m.doctorSpecialty(),
                    m.occurredAt(), m.aggregateVersion()
            );
            case AppointmentCancelledMessage m -> row.applyCancelled(
                    m.appointmentId(), m.patientId(), m.doctorId(),
                    m.scheduledAt(), m.cancelledAt(), m.occurredAt(), m.aggregateVersion()
            );
            case AppointmentCompletedMessage m -> row.applyCompleted(
                    m.appointmentId(), m.patientId(), m.doctorId(),
                    m.scheduledAt(), m.completedAt(), m.occurredAt(), m.aggregateVersion()
            );
        }

        appointments.save(row);
        freshness.markApplied(event.occurredAt());
    }
}
