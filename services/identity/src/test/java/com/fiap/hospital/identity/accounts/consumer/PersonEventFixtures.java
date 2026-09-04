package com.fiap.hospital.identity.accounts.consumer;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.UUID;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.json.JsonMapper;

/**
 * Serializa o envelope como o {@code OutboxEventWriter} do scheduling
 * (cópia local, ADR-0017).
 */
final class PersonEventFixtures {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-17T14:05:03.123Z");

    private PersonEventFixtures() {}

    static String patientRegistered(UUID eventId, UUID patientId, String taxIdentifier) {
        return envelope(eventId, "PatientRegistered", new PatientRegisteredEvent(
            patientId,
            taxIdentifier,
            "Ana Ribeiro",
            "ana.ribeiro@exemplo.com",
            "+5511998877665",
            "PATIENT"
        ));
    }

    static String doctorRegistered(UUID eventId, UUID doctorId, String taxIdentifier) {
        return envelope(eventId, "DoctorRegistered", new DoctorRegisteredEvent(
            doctorId,
            taxIdentifier,
            "CRM-SP 123456",
            "Cardiologia",
            "Dr. Paulo Menezes",
            "paulo.menezes@hospital.local",
            "DOCTOR"
        ));
    }

    static String contactUpdated(UUID eventId, UUID patientId) {
        return envelope(eventId, "PatientContactUpdated", new PatientContactUpdatedEvent(
            patientId,
            "novo@exemplo.com",
            "+5511998877665"
        ));
    }

    private static String envelope(UUID eventId, String eventType, Object data) {
        return MAPPER.writeValueAsString(
            new EventEnvelope(eventId, eventType, 1, OCCURRED_AT, data)
        );
    }

    private record EventEnvelope(
        UUID eventId,
        String eventType,
        int eventVersion,
        @JsonSerialize(using = OccurredAtSerializer.class) Instant occurredAt,
        Object data
    ) {}

    private record PatientRegisteredEvent(
        UUID patientId,
        String taxIdentifier,
        String name,
        String email,
        String phone,
        String role
    ) {}

    private record DoctorRegisteredEvent(
        UUID doctorId,
        String taxIdentifier,
        String crm,
        String specialty,
        String name,
        String email,
        String role
    ) {}

    private record PatientContactUpdatedEvent(UUID patientId, String email, String phone) {}

    private static final class OccurredAtSerializer extends ValueSerializer<Instant> {

        private static final DateTimeFormatter FORMATTER =
            new DateTimeFormatterBuilder().appendInstant(3).toFormatter();

        @Override
        public void serialize(Instant value, JsonGenerator gen, SerializationContext ctxt) {
            gen.writeString(FORMATTER.format(value));
        }
    }
}
