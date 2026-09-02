package com.fiap.hospital.notification.idempotency;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;

@Entity
public class ProcessedEvent {

    @Id
    private UUID eventId;

    protected ProcessedEvent() {}
}
