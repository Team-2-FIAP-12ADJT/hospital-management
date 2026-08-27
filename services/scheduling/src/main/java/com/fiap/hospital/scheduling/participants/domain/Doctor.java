package com.fiap.hospital.scheduling.participants.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "doctor", schema = "participants")
public class Doctor {

    @Id
    private UUID id;

    @Column(name = "tax_identifier", nullable = false, length = 14)
    private String taxIdentifier;

    @Column(nullable = false, length = 20)
    private String crm;

    @Column(nullable = false, length = 80)
    private String specialty;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Doctor() {
    }

    public Doctor(UUID id, String taxIdentifier, String crm, String specialty, String name, String email) {
        this.id = id;
        this.taxIdentifier = taxIdentifier;
        this.crm = crm;
        this.specialty = specialty;
        this.name = name;
        this.email = email;
        this.active = true;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getTaxIdentifier() {
        return taxIdentifier;
    }

    public String getCrm() {
        return crm;
    }

    public String getSpecialty() {
        return specialty;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
