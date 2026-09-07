package com.fiap.hospital.notification.notifications.domain;

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
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "notification")
public class Notification implements Persistable<UUID> {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "appointment_id")
    private UUID appointmentId;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "doctor_name", length = 150)
    private String doctorName;

    @Column(name = "doctor_specialty", length = 80)
    private String doctorSpecialty;

    @Column(name = "fire_at", nullable = false)
    private Instant fireAt;

    @Column(nullable = false)
    private short attempts;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

    protected Notification() {
    }

    private Notification(
        UUID id,
        NotificationKind kind,
        UUID patientId,
        UUID appointmentId,
        Instant scheduledAt,
        String doctorName,
        String doctorSpecialty,
        Instant fireAt,
        Instant createdAt
    ) {
        this.id = require(id, "id");
        this.kind = require(kind, "kind");
        this.status = NotificationStatus.PENDING;
        this.patientId = require(patientId, "patientId");
        this.appointmentId = require(appointmentId, "appointmentId");
        this.scheduledAt = normalize(require(scheduledAt, "scheduledAt"));
        this.doctorName = requireText(doctorName, "doctorName");
        this.doctorSpecialty = requireText(doctorSpecialty, "doctorSpecialty");
        this.fireAt = normalize(require(fireAt, "fireAt"));
        this.attempts = 0;
        this.createdAt = normalize(require(createdAt, "createdAt"));
    }

    public static Notification confirmation(
        UUID id,
        UUID patientId,
        UUID appointmentId,
        Instant scheduledAt,
        String doctorName,
        String doctorSpecialty,
        Instant now
    ) {
        return new Notification(
            id, NotificationKind.CONFIRMATION, patientId, appointmentId,
            scheduledAt, doctorName, doctorSpecialty, now, now
        );
    }

    /** Vazio quando o disparo já venceu: consulta perto demais do horário não gera Reminder. */
    public static Optional<Notification> reminder(
        UUID id,
        UUID patientId,
        UUID appointmentId,
        Instant scheduledAt,
        String doctorName,
        String doctorSpecialty,
        Instant fireAt,
        Instant now
    ) {
        if (!normalize(require(fireAt, "fireAt")).isAfter(normalize(require(now, "now")))) {
            return Optional.empty();
        }
        return Optional.of(new Notification(
            id, NotificationKind.REMINDER, patientId, appointmentId,
            scheduledAt, doctorName, doctorSpecialty, fireAt, now
        ));
    }

    public void markSent(Instant now) {
        requireStatus(NotificationStatus.PENDING);
        this.status = NotificationStatus.SENT;
        this.sentAt = normalize(require(now, "now"));
    }

    public void recordFailedAttempt() {
        requireStatus(NotificationStatus.PENDING);
        this.attempts++;
    }

    private void requireStatus(NotificationStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("notification is not " + expected);
        }
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static Instant normalize(Instant value) {
        return value.truncatedTo(ChronoUnit.MILLIS);
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

    public NotificationKind getKind() { return kind; }
    public NotificationStatus getStatus() { return status; }
    public UUID getPatientId() { return patientId; }
    public UUID getAppointmentId() { return appointmentId; }
    public Instant getScheduledAt() { return scheduledAt; }
    public String getDoctorName() { return doctorName; }
    public String getDoctorSpecialty() { return doctorSpecialty; }
    public Instant getFireAt() { return fireAt; }
    public short getAttempts() { return attempts; }
    public Instant getSentAt() { return sentAt; }
    public Instant getCreatedAt() { return createdAt; }
}
