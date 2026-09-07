package com.fiap.hospital.scheduling.appointments.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "appointment", schema = "scheduling")
public class Appointment implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "doctor_id", nullable = false)
    private UUID doctorId;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppointmentStatus status;

    @Column(name = "fit_in", nullable = false)
    private boolean fitIn;

    @Column(name = "fit_in_reason", length = 255)
    private String fitInReason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

    protected Appointment() {
    }

    private Appointment(
        UUID id,
        UUID patientId,
        UUID doctorId,
        Instant scheduledAt,
        boolean fitIn,
        String fitInReason,
        Instant createdAt
    ) {
        this.id = require(id, "id");
        this.patientId = require(patientId, "patientId");
        this.doctorId = require(doctorId, "doctorId");
        this.scheduledAt = normalize(require(scheduledAt, "scheduledAt"));
        this.status = AppointmentStatus.SCHEDULED;
        this.fitIn = fitIn;
        this.fitInReason = validateFitIn(fitIn, fitInReason);
        this.createdAt = normalize(require(createdAt, "createdAt"));
    }

    public static Appointment schedule(
        UUID id,
        UUID patientId,
        UUID doctorId,
        Instant scheduledAt,
        boolean fitIn,
        String fitInReason,
        Instant now,
        boolean occupied
    ) {
        Instant normalizedNow = normalize(require(now, "now"));
        Instant normalizedScheduledAt = normalize(require(scheduledAt, "scheduledAt"));
        if (normalizedScheduledAt.isBefore(normalizedNow)) {
            throw new IllegalArgumentException("scheduledAt cannot be in the past");
        }
        if (occupied && !fitIn) {
            throw new IllegalStateException("appointment time is already occupied");
        }
        return new Appointment(
            id, patientId, doctorId, normalizedScheduledAt, fitIn, fitInReason, normalizedNow
        );
    }

    public void reschedule(
        Instant newScheduledAt,
        boolean fitIn,
        String fitInReason,
        Instant now,
        boolean occupied
    ) {
        requireState(AppointmentStatus.SCHEDULED);
        Instant normalizedNow = normalize(require(now, "now"));
        if (!scheduledAt.isAfter(normalizedNow)) {
            throw new IllegalStateException("appointment has already started");
        }
        Instant normalizedNewTime = normalize(require(newScheduledAt, "scheduledAt"));
        if (!normalizedNewTime.isAfter(normalizedNow)) {
            throw new IllegalArgumentException("scheduledAt must be in the future");
        }
        if (occupied && !fitIn) {
            throw new IllegalStateException("appointment time is already occupied");
        }
        String validatedFitInReason = validateFitIn(fitIn, fitInReason);
        this.scheduledAt = normalizedNewTime;
        this.fitIn = fitIn;
        this.fitInReason = validatedFitInReason;
    }

    public void cancel(Instant now) {
        requireState(AppointmentStatus.SCHEDULED);
        Instant normalizedNow = normalize(require(now, "now"));
        if (!scheduledAt.isAfter(normalizedNow)) {
            throw new IllegalStateException("appointment has already started");
        }
        status = AppointmentStatus.CANCELLED;
        cancelledAt = normalizedNow;
    }

    public boolean complete() {
        if (status == AppointmentStatus.COMPLETED) {
            return false;
        }
        if (status == AppointmentStatus.CANCELLED) {
            throw new IllegalStateException("cancelled appointment cannot be completed");
        }
        status = AppointmentStatus.COMPLETED;
        return true;
    }

    private void requireState(AppointmentStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("appointment is not " + expected);
        }
    }

    private static String validateFitIn(boolean fitIn, String reason) {
        if (!fitIn && reason != null && !reason.isBlank()) {
            throw new IllegalArgumentException("fitInReason requires fitIn");
        }
        if (fitIn && (reason == null || reason.isBlank() || reason.length() > 255)) {
            throw new IllegalArgumentException("fitInReason is required and must have at most 255 characters");
        }
        return reason;
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    public static Instant normalizeInstant(Instant value) {
        return normalize(value);
    }

    private static Instant normalize(Instant value) {
        return value.truncatedTo(ChronoUnit.MICROS);
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        isNew = false;
    }

    public UUID getPatientId() { return patientId; }
    public UUID getDoctorId() { return doctorId; }
    public Instant getScheduledAt() { return scheduledAt; }
    public AppointmentStatus getStatus() { return status; }
    public boolean isFitIn() { return fitIn; }
    public String getFitInReason() { return fitInReason; }
    public Instant getCancelledAt() { return cancelledAt; }
    public Instant getCreatedAt() { return createdAt; }
}
