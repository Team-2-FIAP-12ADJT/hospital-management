package com.fiap.hospital.history.projection.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "appointment_projection")
public class AppointmentProjection {

    @Id
    @Column(name = "appointment_id", nullable = false)
    private UUID appointmentId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "doctor_id", nullable = false)
    private UUID doctorId;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AppointmentStatus status;

    @Column(name = "fit_in", nullable = false)
    private boolean fitIn;

    @Column(name = "fit_in_reason")
    private String fitInReason;

    @Column(name = "patient_name", nullable = false)
    private String patientName;

    @Column(name = "doctor_name", nullable = false)
    private String doctorName;

    @Column(name = "doctor_specialty", nullable = false)
    private String doctorSpecialty;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public AppointmentProjection() {
    }

    // Empate em occurredAt vale como novo: o envelope trunca o instante em
    // milissegundos, então dois eventos distintos do mesmo agendamento cabem no
    // mesmo carimbo. Tratar empate como velho descartava o segundo para sempre —
    // agendar e cancelar no mesmo milissegundo deixava a projeção em SCHEDULED.
    // Reentrega do mesmo evento continua barrada pelo processed_event (eventId).
    private boolean isStale(Instant appliedAt) {
        return this.updatedAt != null && appliedAt.isBefore(this.updatedAt);
    }

    // CANCELLED e COMPLETED encerram o ciclo. Depois de um deles, só evento
    // estritamente mais novo muda a linha. Sem esta guarda, reprocessar da DLT um
    // reschedule de mesmo occurredAt que a conclusão ressuscita SCHEDULED com
    // completedAt preenchido, porque o empate passa a obedecer à ordem de chegada.
    // ⚠ O desempate correto seria sequência por agregado no envelope, que o
    // scheduling ainda não publica; esta guarda fecha a regressão observável.
    private boolean isTerminal() {
        return this.status == AppointmentStatus.CANCELLED || this.status == AppointmentStatus.COMPLETED;
    }

    private boolean rejectsReopening(Instant appliedAt) {
        return isTerminal() && !appliedAt.isAfter(this.updatedAt);
    }

    public void applyScheduled(
            UUID appointmentId,
            UUID patientId,
            UUID doctorId,
            Instant scheduledAt,
            boolean fitIn,
            String fitInReason,
            String patientName,
            String doctorName,
            String doctorSpecialty,
            Instant appliedAt
    ) {
        if (isStale(appliedAt) || rejectsReopening(appliedAt)) return;
        this.appointmentId = appointmentId;
        this.patientId = patientId;
        this.doctorId = doctorId;
        this.scheduledAt = scheduledAt;
        this.status = AppointmentStatus.SCHEDULED;
        this.fitIn = fitIn;
        this.fitInReason = fitInReason;
        this.patientName = patientName;
        this.doctorName = doctorName;
        this.doctorSpecialty = doctorSpecialty;
        this.updatedAt = appliedAt;
    }

    public void applyRescheduled(
            UUID appointmentId,
            UUID patientId,
            UUID doctorId,
            Instant scheduledAt,
            boolean fitIn,
            String fitInReason,
            String patientName,
            String doctorName,
            String doctorSpecialty,
            Instant appliedAt
    ) {
        if (isStale(appliedAt) || rejectsReopening(appliedAt)) return;
        this.appointmentId = appointmentId;
        this.patientId = patientId;
        this.doctorId = doctorId;
        this.scheduledAt = scheduledAt;
        this.status = AppointmentStatus.SCHEDULED;
        this.fitIn = fitIn;
        this.fitInReason = fitInReason;
        this.patientName = patientName;
        this.doctorName = doctorName;
        this.doctorSpecialty = doctorSpecialty;
        this.updatedAt = appliedAt;
    }

    public void applyCancelled(
            UUID appointmentId,
            UUID patientId,
            UUID doctorId,
            Instant scheduledAt,
            Instant cancelledAt,
            Instant appliedAt
    ) {
        if (isStale(appliedAt) || rejectsReopening(appliedAt)) return;
        this.appointmentId = appointmentId;
        this.patientId = patientId;
        this.doctorId = doctorId;
        this.scheduledAt = scheduledAt;
        this.status = AppointmentStatus.CANCELLED;
        this.cancelledAt = cancelledAt;
        this.updatedAt = appliedAt;
    }

    public void applyCompleted(
            UUID appointmentId,
            UUID patientId,
            UUID doctorId,
            Instant scheduledAt,
            Instant completedAt,
            Instant appliedAt
    ) {
        if (isStale(appliedAt) || rejectsReopening(appliedAt)) return;
        this.appointmentId = appointmentId;
        this.patientId = patientId;
        this.doctorId = doctorId;
        this.scheduledAt = scheduledAt;
        this.status = AppointmentStatus.COMPLETED;
        this.completedAt = completedAt;
        this.updatedAt = appliedAt;
    }

    public UUID getAppointmentId() {
        return appointmentId;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public UUID getDoctorId() {
        return doctorId;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public boolean isFitIn() {
        return fitIn;
    }

    public String getFitInReason() {
        return fitInReason;
    }

    public String getPatientName() {
        return patientName;
    }

    public String getDoctorName() {
        return doctorName;
    }

    public String getDoctorSpecialty() {
        return doctorSpecialty;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
