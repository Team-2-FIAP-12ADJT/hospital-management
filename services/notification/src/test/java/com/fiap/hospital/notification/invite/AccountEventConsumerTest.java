package com.fiap.hospital.notification.invite;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountEventConsumerTest {

    @Mock
    private SendActivationInvite sendActivationInvite;

    @Test
    void discardsMalformedJson() {
        receive("{not-json");

        verifyNoInteractions(sendActivationInvite);
    }

    @Test
    void discardsEnvelopeMissingRequiredField() {
        receive("{\"eventId\":\"" + UUID.randomUUID() + "\"}");

        verifyNoInteractions(sendActivationInvite);
    }

    @Test
    void ignoresUnsupportedEventType() {
        receive(AccountEventFixtures.patientRegistered(UUID.randomUUID(), UUID.randomUUID()));

        verifyNoInteractions(sendActivationInvite);
    }

    @Test
    void discardsInvalidEmailWithoutSending() {
        receive(AccountEventFixtures.userActivationRequested(
            UUID.randomUUID(), UUID.randomUUID(), "tok", "not-an-email"
        ));

        verifyNoInteractions(sendActivationInvite);
    }

    @Test
    void sendsInviteForUserActivationRequested() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        receive(AccountEventFixtures.userActivationRequested(eventId, userId, "token-claro"));

        verify(sendActivationInvite).send(any(ActivationInvite.class));
    }

    private void receive(String value) {
        new AccountEventConsumer(new ActivationInviteParser(), sendActivationInvite)
            .receive(new ConsumerRecord<>("hospital.account", 0, 0L, null, value));
    }
}
