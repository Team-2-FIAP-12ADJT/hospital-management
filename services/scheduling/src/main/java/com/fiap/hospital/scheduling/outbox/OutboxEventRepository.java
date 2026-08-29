package com.fiap.hospital.scheduling.outbox;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository
    extends JpaRepository<OutboxEvent, UUID> {}
