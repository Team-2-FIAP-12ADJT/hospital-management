package com.fiap.hospital.notification.idempotency;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessedEventRepository
    extends JpaRepository<ProcessedEvent, ProcessedEvent.Key> {

    @Modifying
    @Query(
        value = """
            INSERT INTO processed_event (event_id, consumer)
            VALUES (:eventId, :consumer)
            ON CONFLICT DO NOTHING
            """,
        nativeQuery = true
    )
    int insertIfAbsent(@Param("eventId") UUID eventId, @Param("consumer") String consumer);
}
