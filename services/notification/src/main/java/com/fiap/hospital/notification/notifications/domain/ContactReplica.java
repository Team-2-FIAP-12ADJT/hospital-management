package com.fiap.hospital.notification.notifications.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "contact_replica")
public class ContactReplica {

    @Id
    @Column(name = "patient_id")
    private UUID patientId;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ContactReplica() {
    }

    public UUID getPatientId() { return patientId; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public Instant getUpdatedAt() { return updatedAt; }
}
