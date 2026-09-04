package com.fiap.hospital.identity.accounts.repository;

import com.fiap.hospital.identity.accounts.domain.ActivationToken;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivationTokenRepository extends JpaRepository<ActivationToken, UUID> {
    long countByUserId(UUID userId);
}
