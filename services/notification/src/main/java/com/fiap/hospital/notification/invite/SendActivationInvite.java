package com.fiap.hospital.notification.invite;

import com.fiap.hospital.notification.idempotency.IdempotencyService;
import com.fiap.hospital.notification.mail.MailFailure;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;

@Service
class SendActivationInvite {

    private static final Logger log = LoggerFactory.getLogger(SendActivationInvite.class);

    private static final String CONSUMER = "invite";

    private final IdempotencyService idempotencyService;
    private final ActivationInviteMailer mailer;

    SendActivationInvite(
        IdempotencyService idempotencyService,
        ActivationInviteMailer mailer
    ) {
        this.idempotencyService = idempotencyService;
        this.mailer = mailer;
    }

    /**
     * Marca o evento como processado SEM enviar. Só para recusa definitiva de
     * conteúdo: sem isto o mesmo evento quebrado voltaria a cada replay, e com isto
     * ele para de voltar sem que nada tenha sido enviado — que é exatamente o que
     * "grava idempotência e descarta de propósito" quer dizer.
     */
    void discardPermanently(UUID eventId) {
        idempotencyService.process(CONSUMER, eventId, () -> { });
    }

    void send(ActivationInvite invite) {
        idempotencyService.process(CONSUMER, invite.eventId(), () -> {
            try {
                mailer.send(invite);
            } catch (MailException ex) {
                if (MailFailure.isTransient(ex)) {
                    throw ex;
                }
                log.error(
                    "dropping activation invite eventId={} userId={} due to permanent mail failure",
                    invite.eventId(),
                    invite.userId()
                );
                return;
            }
            log.info(
                "activation invite sent eventId={} userId={}",
                invite.eventId(),
                invite.userId()
            );
        });
    }
}
