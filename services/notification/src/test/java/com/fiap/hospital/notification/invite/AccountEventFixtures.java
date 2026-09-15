package com.fiap.hospital.notification.invite;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.UUID;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.json.JsonMapper;

final class AccountEventFixtures {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-17T14:05:03.123Z");
    static final Instant EXPIRES_AT = Instant.parse("2026-08-18T14:05:03.123Z");

    private AccountEventFixtures() {}

    static String userActivationRequested(UUID eventId, UUID userId, String token) {
        return userActivationRequested(eventId, userId, token, "ana.ribeiro@exemplo.com");
    }

    static String userActivationRequested(
        UUID eventId,
        UUID userId,
        String token,
        String email
    ) {
        return envelope(eventId, "UserActivationRequested", new UserActivationRequestedEvent(
            userId,
            "Ana Ribeiro",
            email,
            "PATIENT",
            token,
            EXPIRES_AT
        ));
    }

    static String patientRegistered(UUID eventId, UUID patientId) {
        return envelope(eventId, "PatientRegistered", new PatientRegisteredEvent(patientId));
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

    private record UserActivationRequestedEvent(
        UUID userId,
        String name,
        String email,
        String role,
        String activationToken,
        @JsonSerialize(using = OccurredAtSerializer.class) Instant expiresAt
    ) {}

    private record PatientRegisteredEvent(UUID patientId) {}

    private static final class OccurredAtSerializer extends ValueSerializer<Instant> {

        private static final DateTimeFormatter FORMATTER =
            new DateTimeFormatterBuilder().appendInstant(3).toFormatter();

        @Override
        public void serialize(Instant value, JsonGenerator gen, SerializationContext ctxt) {
            gen.writeString(FORMATTER.format(value));
        }
    }
}
