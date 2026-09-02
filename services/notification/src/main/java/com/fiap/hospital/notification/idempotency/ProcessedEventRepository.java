package com.fiap.hospital.notification.idempotency;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ProcessedEventRepository
    extends JpaRepository<ProcessedEvent, UUID> {

    @Modifying
    @Query(
        value = """
            INSERT INTO processed_event (event_id)
            VALUES (:eventId)
            ON CONFLICT DO NOTHING
            """,
        nativeQuery = true
    )
    int insertIfAbsent(UUID eventId);
}
