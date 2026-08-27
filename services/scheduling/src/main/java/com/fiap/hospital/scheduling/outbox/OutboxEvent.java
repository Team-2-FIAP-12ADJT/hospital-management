package com.fiap.hospital.scheduling.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(nullable = false, length = 100)
    private String type;

    @Column(nullable = false)
    private int version;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(nullable = false, length = 100)
    private String topic;

    protected OutboxEvent() {
    }

    public OutboxEvent(UUID id, String aggregateType, UUID aggregateId, String type, int version,
                        Instant occurredAt, String payload, String topic) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.type = type;
        this.version = version;
        this.occurredAt = occurredAt;
        this.payload = payload;
        this.topic = topic;
    }

    public UUID getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getType() {
        return type;
    }

    public int getVersion() {
        return version;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getPayload() {
        return payload;
    }

    public String getTopic() {
        return topic;
    }
}
