package com.fiap.hospital.history.projection.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "projection_freshness")
public class ProjectionFreshness {

    @Id
    @Column(name = "id", nullable = false)
    private Short id;

    @Column(name = "last_applied_at")
    private Instant lastAppliedAt;

    protected ProjectionFreshness() {
    }

    public Instant getLastAppliedAt() {
        return lastAppliedAt;
    }
}
