package com.fiap.hospital.identity.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "outbox_events", schema = "public")
public class OutboxEvent implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "type", nullable = false, length = 80)
    private String type;

    @Column(name = "version", nullable = false)
    private Integer eventVersion;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "envelope", nullable = false, columnDefinition = "TEXT")
    private String envelope;

    @Column(name = "topic", nullable = false, length = 100)
    private String topic;

    @Transient
    private boolean isNew = true;

    protected OutboxEvent() {}

    public static OutboxEvent create(
        UUID id,
        String aggregateType,
        UUID aggregateId,
        String type,
        Integer eventVersion,
        Instant occurredAt,
        String envelope,
        String topic
    ) {
        OutboxEvent event = new OutboxEvent();
        event.id = id;
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.type = type;
        event.eventVersion = eventVersion;
        event.occurredAt = occurredAt;
        event.envelope = envelope;
        event.topic = topic;
        return event;
    }

    public UUID id() { return id; }

    @Override
    public UUID getId() { return id; }

    @Override
    public boolean isNew() { return isNew; }

    @PostPersist
    @PostLoad
    void markNotNew() { isNew = false; }

    public String aggregateType() { return aggregateType; }
    public UUID aggregateId() { return aggregateId; }
    public String type() { return type; }
    public Integer eventVersion() { return eventVersion; }
    public Instant occurredAt() { return occurredAt; }
    public String envelope() { return envelope; }
    public String topic() { return topic; }
}
