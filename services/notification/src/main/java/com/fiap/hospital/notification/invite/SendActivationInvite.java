package com.fiap.hospital.notification.invite;

import com.fiap.hospital.notification.idempotency.IdempotencyService;
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
