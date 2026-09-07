package com.fiap.hospital.notification.invite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActivationInviteParserTest {

    private final ActivationInviteParser parser = new ActivationInviteParser();

    @Test
    void leOsCamposDoContrato() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        ActivationInvite invite = parser.parse(
            AccountEventFixtures.userActivationRequested(eventId, userId, "3Yb9Qk2Lm7Rx0Tn5")
        );

        assertThat(invite.eventId()).isEqualTo(eventId);
        assertThat(invite.userId()).isEqualTo(userId);
        assertThat(invite.name()).isEqualTo("Ana Ribeiro");
        assertThat(invite.email()).isEqualTo("ana.ribeiro@exemplo.com");
        assertThat(invite.role()).isEqualTo("PATIENT");
        assertThat(invite.activationToken()).isEqualTo("3Yb9Qk2Lm7Rx0Tn5");
        assertThat(invite.expiresAt()).isEqualTo(AccountEventFixtures.EXPIRES_AT);
    }

    @Test
    void ignoraTipoForaDoContratoDeConta() {
        assertThatThrownBy(() ->
            parser.parse(AccountEventFixtures.patientRegistered(
                UUID.randomUUID(), UUID.randomUUID()
            ))
        ).isInstanceOf(UnsupportedAccountEventException.class);
    }

    @Test
    void recusaEventIdNaoCanonico() {
        assertThatThrownBy(() ->
            parser.parse(
                "{\"eventId\":\"1-1-1-1-1\",\"eventType\":\"UserActivationRequested\","
                    + "\"eventVersion\":1,\"occurredAt\":\"2026-08-17T14:05:03.123Z\","
                    + "\"data\":{\"userId\":\"" + UUID.randomUUID()
                    + "\",\"name\":\"Ana\",\"email\":\"a@b.c\",\"role\":\"PATIENT\","
                    + "\"activationToken\":\"tok\",\"expiresAt\":\"2026-08-18T14:05:03.123Z\"}}"
            )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recusaDataSemToken() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String json = AccountEventFixtures.userActivationRequested(eventId, userId, "tok")
            .replace("\"activationToken\":\"tok\"", "\"activationToken\":\"\"");

        assertThatThrownBy(() -> parser.parse(json))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recusaEmailSemFormatoDeEndereco() {
        assertThatThrownBy(() ->
            parser.parse(AccountEventFixtures.userActivationRequested(
                UUID.randomUUID(), UUID.randomUUID(), "tok", "not-an-email"
            ))
        ).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("email");
    }

    @Test
    void recusaEmailComDestinatarioExtraOuQuebraDeLinha() {
        assertThatThrownBy(() ->
            parser.parse(AccountEventFixtures.userActivationRequested(
                UUID.randomUUID(), UUID.randomUUID(), "tok",
                "ana@exemplo.com,outro@evil.com"
            ))
        ).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
            parser.parse(AccountEventFixtures.userActivationRequested(
                UUID.randomUUID(), UUID.randomUUID(), "tok",
                "ana@exemplo.com\r\nBcc: outro@evil.com"
            ))
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
