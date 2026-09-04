package com.fiap.hospital.history.projection.repository;

import com.fiap.hospital.history.projection.domain.ProjectionFreshness;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectionFreshnessRepository extends JpaRepository<ProjectionFreshness, Short> {
}
