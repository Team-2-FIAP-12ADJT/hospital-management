package com.fiap.hospital.identity.accounts.consumer;

import com.fiap.hospital.identity.accounts.domain.Role;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
class PersonRegistrationParser {

    static final String PATIENT_REGISTERED = "PatientRegistered";
    static final String DOCTOR_REGISTERED = "DoctorRegistered";

    private static final Set<String> SUPPORTED_TYPES = Set.of(PATIENT_REGISTERED, DOCTOR_REGISTERED);

    private final JsonMapper mapper = JsonMapper.builder().build();

    /**
     * Lê só os campos do contrato. Campo desconhecido em {@code data} é ignorado
     * (CRM, especialidade, phone, campo aditivo de eventVersion).
     */
    PersonRegistration parse(String envelopeJson) {
        JsonNode root = mapper.readTree(envelopeJson);
        String eventType = requiredText(root, "eventType");
        if (!SUPPORTED_TYPES.contains(eventType)) {
            throw new UnsupportedPersonEventException(eventType);
        }

        JsonNode data = root.get("data");
        if (data == null || !data.isObject()) {
            throw new IllegalArgumentException("envelope sem data");
        }

        UUID personId = UUID.fromString(requiredText(data, personIdField(eventType)));
        return new PersonRegistration(
            UUID.fromString(requiredText(root, "eventId")),
            eventType,
            personId,
            requiredText(data, "taxIdentifier"),
            requiredText(data, "name"),
            requiredText(data, "email"),
            Role.valueOf(requiredText(data, "role"))
        );
    }

    private static String personIdField(String eventType) {
        return PATIENT_REGISTERED.equals(eventType) ? "patientId" : "doctorId";
    }

    private static String requiredText(JsonNode node, String field) {
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
