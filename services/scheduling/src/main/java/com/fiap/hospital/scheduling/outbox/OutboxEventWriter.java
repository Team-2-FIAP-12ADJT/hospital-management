package com.fiap.hospital.scheduling.outbox;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Component
public class OutboxEventWriter {
    private final OutboxEventRepository repository;
    private final JsonMapper mapper;

    public OutboxEventWriter(OutboxEventRepository repository, JsonMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID append(Aggregate aggregate, UUID aggregateId, String eventType,
                       int eventVersion, Instant occurredAt, Object data) {
        UUID eventId = UUID.randomUUID();
        Instant truncatedOccurredAt = occurredAt.truncatedTo(java.time.temporal.ChronoUnit.MILLIS);

        EventEnvelope envelope = new EventEnvelope(eventId, eventType, eventVersion, truncatedOccurredAt, data);
        String envelopeJson = mapper.writeValueAsString(envelope);

        OutboxEvent event = OutboxEvent.create(
            eventId,
            aggregate.type(),
            aggregateId,
            eventType,
            eventVersion,
            truncatedOccurredAt,
            envelopeJson,
            aggregate.topic()
        );

        repository.save(event);
        return eventId;
    }
}
