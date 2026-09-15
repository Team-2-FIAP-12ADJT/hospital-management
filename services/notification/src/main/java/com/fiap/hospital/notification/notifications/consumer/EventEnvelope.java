package com.fiap.hospital.notification.notifications.consumer;

import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

record EventEnvelope(UUID eventId, String eventType, Instant occurredAt, JsonNode data) {

    static EventEnvelope from(JsonNode root) {
        JsonNode data = root.get("data");
        if (data == null || !data.isObject()) {
            throw new IllegalArgumentException("envelope sem data");
        }
        return new EventEnvelope(
            uuid(root, "eventId"),
            text(root, "eventType"),
            instant(root, "occurredAt"),
            data
        );
    }

    String requiredText(String field) {
        return text(data, field);
    }

    String optionalText(String field) {
        JsonNode value = data.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String asText = value.asString();
        return asText == null || asText.isBlank() ? null : asText;
    }

    UUID requiredUuid(String field) {
        return uuid(data, field);
    }

    Instant requiredInstant(String field) {
        return instant(data, field);
    }

    private static UUID uuid(JsonNode node, String field) {
        return UUID.fromString(text(node, field));
    }

    private static Instant instant(JsonNode node, String field) {
        return Instant.parse(text(node, field));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("campo obrigatório ausente: " + field);
        }
        String asText = value.asString();
        if (asText == null || asText.isBlank()) {
            throw new IllegalArgumentException("campo obrigatório ausente: " + field);
        }
        return asText;
    }
}
