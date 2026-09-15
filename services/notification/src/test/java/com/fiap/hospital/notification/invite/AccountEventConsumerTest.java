package com.fiap.hospital.notification.invite;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
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
    void propagatesMalformedJsonForTheContainerErrorHandlerToClassify() {
        assertThrows(RuntimeException.class, () -> receive("{not-json"));

        verifyNoInteractions(sendActivationInvite);
    }

    @Test
    void propagatesEnvelopeMissingRequiredFieldForTheContainerErrorHandlerToClassify() {
        assertThrows(RuntimeException.class,
            () -> receive("{\"eventId\":\"" + UUID.randomUUID() + "\"}"));

        verifyNoInteractions(sendActivationInvite);
    }

    @Test
    void ignoresUnsupportedEventType() {
        receive(AccountEventFixtures.patientRegistered(UUID.randomUUID(), UUID.randomUUID()));

        verifyNoInteractions(sendActivationInvite);
    }

    // Recusa definitiva de conteudo NAO propaga: iria para a DLT e levaria o token
    // de ativacao em claro para um topico legivel no kafbat-ui, que e publicado.
    // Grava idempotencia e descarta de proposito.
    @Test
    void invalidEmailIsDiscardedPermanentlyWithIdempotencyRecordedAndWithoutSending() {
        UUID eventId = UUID.randomUUID();

        receive(AccountEventFixtures.userActivationRequested(
            eventId, UUID.randomUUID(), "tok", "not-an-email"
        ));

        verify(sendActivationInvite).discardPermanently(eventId);
        verify(sendActivationInvite, never()).send(any());
    }

    // Campo ausente ou em branco tambem esta no mesmo payload do token: mandar para
    // a DLT por ser "malformado" vazaria o segredo do mesmo jeito.
    @Test
    void blankEmailIsDiscardedPermanentlyInsteadOfGoingToTheDlt() {
        UUID eventId = UUID.randomUUID();

        receive(AccountEventFixtures.userActivationRequested(
            eventId, UUID.randomUUID(), "tok", "   "
        ));

        verify(sendActivationInvite).discardPermanently(eventId);
        verify(sendActivationInvite, never()).send(any());
    }

    @Test
    void headerInjectionInTheEmailIsDiscardedTheSameWay() {
        UUID eventId = UUID.randomUUID();

        receive(AccountEventFixtures.userActivationRequested(
            eventId, UUID.randomUUID(), "tok", "vitima@exemplo.test\r\nBcc: atacante@exemplo.test"
        ));

        verify(sendActivationInvite).discardPermanently(eventId);
        verify(sendActivationInvite, never()).send(any());
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
