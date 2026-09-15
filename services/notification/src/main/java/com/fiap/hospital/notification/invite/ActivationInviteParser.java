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

        // O eventId sai primeiro e sozinho: o e-mail e recusa DEFINITIVA, tratada com
        // idempotencia gravada e descarte deliberado, e sem o identificador nao
        // haveria o que gravar — o evento voltaria a cada replay.
        UUID eventId = canonicalUuid(requiredText(root, "eventId"), "eventId");

        return new ActivationInvite(
            eventId,
            canonicalUuid(requiredText(data, "userId"), "userId"),
            requiredText(data, "name"),
            requiredEmail(data, eventId),
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

    // CR, LF e vírgula são recusa de injeção de cabeçalho SMTP, não capricho de
    // formato: `vitima@x.com\r\nBcc: atacante@exemplo` viraria um segundo destinatário.
    private static String requiredEmail(JsonNode data, UUID eventId) {
        // Ausente ou em branco tambem e recusa DEFINITIVA, nao envelope malformado:
        // `requiredText` lancaria IllegalArgumentException pura, o registro iria para
        // a DLT e levaria o token de ativacao em claro junto. O campo esta no MESMO
        // payload do token, entao todo defeito dele termina em descarte deliberado.
        JsonNode value = data.get("email");
        if (value == null || value.isNull()
            || value.asString() == null || value.asString().isBlank()) {
            throw new RejectedActivationInviteException(eventId, "email");
        }

        String email = value.asString();
        if (email.indexOf('\r') >= 0
            || email.indexOf('\n') >= 0
            || email.indexOf(',') >= 0) {
            throw new RejectedActivationInviteException(eventId, "email");
        }
        try {
            new InternetAddress(email, true).validate();
        } catch (AddressException ex) {
            throw new RejectedActivationInviteException(eventId, "email");
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
