package com.fiap.hospital.scheduling.participants.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "patient", schema = "participants")
public class Patient implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "tax_identifier", nullable = false, length = 14)
    private String taxIdentifier;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

    protected Patient() {}

    public Patient(
        UUID id,
        String taxIdentifier,
        String name,
        String email,
        String phone
    ) {
        this.id = id;
        this.taxIdentifier = taxIdentifier;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.active = true;
        this.createdAt = Instant.now();
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

    public String getTaxIdentifier() {
        return taxIdentifier;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
