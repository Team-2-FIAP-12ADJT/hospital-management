package com.fiap.hospital.identity.accounts.repository;

import com.fiap.hospital.identity.accounts.domain.ActivationToken;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActivationTokenRepository extends JpaRepository<ActivationToken, UUID> {
    long countByUserId(UUID userId);

    Optional<ActivationToken> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM ActivationToken t WHERE t.tokenHash = :tokenHash")
    Optional<ActivationToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);
}
