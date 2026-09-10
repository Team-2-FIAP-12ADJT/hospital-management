package com.fiap.hospital.history.projection.consumer;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

@Component
class AppointmentEventParser {

    static final String APPOINTMENT_SCHEDULED = "AppointmentScheduled";

    private final JsonMapper mapper = JsonMapper.builder().build();

    AppointmentScheduledMessage parse(String envelopeJson) {
        JsonNode root = mapper.readTree(envelopeJson);
        String eventType = requiredText(root, "eventType");
        if (!APPOINTMENT_SCHEDULED.equals(eventType)) {
            throw new UnsupportedAppointmentEventException(eventType);
        }

        JsonNode data = root.get("data");
        if (data == null || !data.isObject()) {
            throw new IllegalArgumentException("envelope sem data");
        }

        String status = requiredText(data, "status");
        if (!"SCHEDULED".equals(status)) {
            throw new IllegalArgumentException("status inválido: " + status);
        }

        return new AppointmentScheduledMessage(
                UUID.fromString(requiredText(root, "eventId")),
                Instant.parse(requiredText(root, "occurredAt")),
                UUID.fromString(requiredText(data, "appointmentId")),
                UUID.fromString(requiredText(data, "patientId")),
                UUID.fromString(requiredText(data, "doctorId")),
                Instant.parse(requiredText(data, "scheduledAt")),
                requiredBoolean(data, "fitIn"),
                optionalText(data, "fitInReason"),
                requiredText(data, "patientName"),
                requiredText(data, "doctorName"),
                requiredText(data, "doctorSpecialty")
        );
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isString() || value.asString().isBlank()) {
            throw new IllegalArgumentException("campo obrigatório ausente: " + field);
        }
        return value.asString();
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isString() || value.asString().isBlank()) {
            return null;
        }
        return value.asString();
    }

    private static boolean requiredBoolean(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isBoolean()) {
            throw new IllegalArgumentException("campo obrigatório ausente: " + field);
        }
        return value.asBoolean();
    }
}
