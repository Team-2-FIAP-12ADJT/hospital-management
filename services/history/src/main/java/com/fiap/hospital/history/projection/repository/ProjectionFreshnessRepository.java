package com.fiap.hospital.history.projection.repository;

import com.fiap.hospital.history.projection.domain.ProjectionFreshness;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface ProjectionFreshnessRepository extends JpaRepository<ProjectionFreshness, Short> {

    @Modifying
    @Query(
            value = """
                    INSERT INTO projection_freshness (id, last_applied_at)
                    VALUES (1, :occurredAt)
                    ON CONFLICT (id) DO UPDATE
                    SET last_applied_at = EXCLUDED.last_applied_at
                    WHERE projection_freshness.last_applied_at IS NULL
                       OR projection_freshness.last_applied_at < EXCLUDED.last_applied_at
                    """,
            nativeQuery = true
    )
    void markApplied(@Param("occurredAt") Instant occurredAt);
}
