package com.fiap.hospital.scheduling.outbox;

import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.annotation.JsonSerialize;

public record EventEnvelope(
    UUID eventId,
    String eventType,
    int eventVersion,
    @JsonSerialize(using = MillisecondInstantSerializer.class) Instant occurredAt,
    // Versao do agregado que produziu o fato (ex.: @Version do Appointment). Monotona
    // por agregado, null quando o agregado nao e versionado (ex.: Patient, Doctor).
    // Fica antes de `data` para o corpo do evento continuar sendo o ultimo campo do
    // JSON, como o contrato mostra.
    Long aggregateVersion,
    Object data
) {}
