package com.fiap.hospital.identity.outbox;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    long countByAggregateId(UUID aggregateId);
}
