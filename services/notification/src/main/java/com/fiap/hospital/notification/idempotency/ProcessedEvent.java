package com.fiap.hospital.notification.idempotency;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@IdClass(ProcessedEvent.Key.class)
public class ProcessedEvent {

    @Id
    private UUID eventId;

    @Id
    private String consumer;

    protected ProcessedEvent() {}

    public static class Key implements Serializable {

        private UUID eventId;
        private String consumer;

        protected Key() {}

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof Key key
                && Objects.equals(eventId, key.eventId)
                && Objects.equals(consumer, key.consumer);
        }

        @Override
        public int hashCode() {
            return Objects.hash(eventId, consumer);
        }
    }
}
