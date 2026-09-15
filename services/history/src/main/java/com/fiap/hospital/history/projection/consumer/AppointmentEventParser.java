package com.fiap.hospital.history.projection.consumer;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;

@Component
class AppointmentEventParser {

    static final String APPOINTMENT_SCHEDULED = "AppointmentScheduled";
    static final String APPOINTMENT_RESCHEDULED = "AppointmentRescheduled";
    static final String APPOINTMENT_CANCELLED = "AppointmentCancelled";
    static final String APPOINTMENT_COMPLETED = "AppointmentCompleted";

    private final JsonMapper mapper = JsonMapper.builder().build();

    AppointmentEvent parse(String envelopeJson) {
        JsonNode root = readEnvelope(envelopeJson);
        String eventType = requiredText(root, "eventType");
        JsonNode data = root.get("data");
        if (data == null || !data.isObject()) {
            throw new IllegalArgumentException("envelope sem data");
        }

        return switch (eventType) {
            case APPOINTMENT_SCHEDULED -> parseScheduled(root, data);
            case APPOINTMENT_RESCHEDULED -> parseRescheduled(root, data);
            case APPOINTMENT_CANCELLED -> parseCancelled(root, data);
            case APPOINTMENT_COMPLETED -> parseCompleted(root, data);
            default -> throw new UnsupportedAppointmentEventException(eventType);
        };
    }

    private AppointmentScheduledMessage parseScheduled(JsonNode root, JsonNode data) {
        requireStatus(data, "SCHEDULED");
        return new AppointmentScheduledMessage(
                eventId(root),
                occurredAt(root),
                appointmentId(data),
                requiredUuid(data, "patientId"),
                requiredUuid(data, "doctorId"),
                requiredInstant(data, "scheduledAt"),
                requiredBoolean(data, "fitIn"),
                optionalText(data, "fitInReason"),
                requiredText(data, "patientName"),
                requiredText(data, "doctorName"),
                requiredText(data, "doctorSpecialty")
        );
    }

    private AppointmentRescheduledMessage parseRescheduled(JsonNode root, JsonNode data) {
        requireStatus(data, "SCHEDULED");
        return new AppointmentRescheduledMessage(
                eventId(root),
                occurredAt(root),
                appointmentId(data),
                requiredUuid(data, "patientId"),
                requiredUuid(data, "doctorId"),
                requiredInstant(data, "previousScheduledAt"),
                requiredInstant(data, "scheduledAt"),
                requiredBoolean(data, "fitIn"),
                optionalText(data, "fitInReason"),
                requiredText(data, "patientName"),
                requiredText(data, "doctorName"),
                requiredText(data, "doctorSpecialty")
        );
    }

    private AppointmentCancelledMessage parseCancelled(JsonNode root, JsonNode data) {
        requireStatus(data, "CANCELLED");
        return new AppointmentCancelledMessage(
                eventId(root),
                occurredAt(root),
                appointmentId(data),
                requiredUuid(data, "patientId"),
                requiredUuid(data, "doctorId"),
                requiredInstant(data, "scheduledAt"),
                requiredInstant(data, "cancelledAt")
        );
    }

    private AppointmentCompletedMessage parseCompleted(JsonNode root, JsonNode data) {
        requireStatus(data, "COMPLETED");
        return new AppointmentCompletedMessage(
                eventId(root),
                occurredAt(root),
                appointmentId(data),
                requiredUuid(data, "patientId"),
                requiredUuid(data, "doctorId"),
                requiredInstant(data, "scheduledAt"),
                requiredInstant(data, "completedAt")
        );
    }

    // A exceção do Jackson repete o trecho recusado do documento — token, campo,
    // valor. Esse trecho é payload do paciente, então nem a mensagem nem a causa
    // podem sobreviver: o que sai daqui é classificação, sem encadeamento.
    private JsonNode readEnvelope(String envelopeJson) {
        try {
            return mapper.readTree(envelopeJson);
        } catch (JacksonException cause) {
            throw new MalformedAppointmentEventException("envelope não é JSON válido");
        }
    }

    private static void requireStatus(JsonNode data, String expected) {
        if (!expected.equals(requiredText(data, "status"))) {
            throw new IllegalArgumentException("status inválido");
        }
    }

    private static UUID eventId(JsonNode root) {
        return requiredUuid(root, "eventId");
    }

    private static Instant occurredAt(JsonNode root) {
        return requiredInstant(root, "occurredAt");
    }

    private static UUID appointmentId(JsonNode data) {
        return requiredUuid(data, "appointmentId");
    }

    private static UUID requiredUuid(JsonNode node, String field) {
        String raw = requiredText(node, field);
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException cause) {
            // A mensagem original repete o valor recusado; só o campo pode ir ao log.
            throw MalformedAppointmentEventException.invalidField(field, cause);
        }
    }

    private static Instant requiredInstant(JsonNode node, String field) {
        String raw = requiredText(node, field);
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException cause) {
            // DateTimeParseException não é IllegalArgumentException: sem esta conversão
            // uma data inválida seria reentregue três vezes antes da DLT.
            throw MalformedAppointmentEventException.invalidField(field, cause);
        }
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
