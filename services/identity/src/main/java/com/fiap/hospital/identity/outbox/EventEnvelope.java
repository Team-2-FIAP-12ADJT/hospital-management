package com.fiap.hospital.identity.outbox;

import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.annotation.JsonSerialize;

public record EventEnvelope(
    UUID eventId,
    String eventType,
    int eventVersion,
    @JsonSerialize(using = OccurredAtSerializer.class) Instant occurredAt,
    Object data
) {}
