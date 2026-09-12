package com.fiap.hospital.notification.invite;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
class ActivationInviteParser {

    static final String USER_ACTIVATION_REQUESTED = "UserActivationRequested";

    private final JsonMapper mapper = JsonMapper.builder().build();

    ActivationInvite parse(String envelopeJson) {
        JsonNode root = mapper.readTree(envelopeJson);
        String eventType = requiredText(root, "eventType");
        if (!USER_ACTIVATION_REQUESTED.equals(eventType)) {
            throw new UnsupportedAccountEventException();
        }

        JsonNode data = root.get("data");
        if (data == null || !data.isObject()) {
            throw new IllegalArgumentException("envelope sem data");
        }

        return new ActivationInvite(
            canonicalUuid(requiredText(root, "eventId"), "eventId"),
            canonicalUuid(requiredText(data, "userId"), "userId"),
            requiredText(data, "name"),
            requiredEmail(data),
            requiredText(data, "role"),
            requiredText(data, "activationToken"),
            Instant.parse(requiredText(data, "expiresAt"))
        );
    }

    private static UUID canonicalUuid(String value, String field) {
        UUID parsed = UUID.fromString(value);
        if (!parsed.toString().equalsIgnoreCase(value)) {
            throw new IllegalArgumentException(field + " is not canonical");
        }
        return parsed;
    }

    private static String requiredEmail(JsonNode data) {
        String email = requiredText(data, "email");
        if (email.indexOf('\r') >= 0
            || email.indexOf('\n') >= 0
            || email.indexOf(',') >= 0) {
            throw new IllegalArgumentException("campo obrigatório inválido: email");
        }
        try {
            new InternetAddress(email, true).validate();
        } catch (AddressException ex) {
            throw new IllegalArgumentException("campo obrigatório inválido: email");
        }
        return email;
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
