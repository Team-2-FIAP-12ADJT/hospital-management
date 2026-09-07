package com.fiap.hospital.notification.invite;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;
import java.net.ConnectException;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;

class MailFailureTest {

    @Test
    void smtpDownIsTransient() {
        assertThat(MailFailure.isTransient(new MailSendException("smtp down"))).isTrue();
    }

    @Test
    void connectionRefusedIsTransient() {
        assertThat(MailFailure.isTransient(
            new MailSendException("could not connect", new ConnectException("refused"))
        )).isTrue();
    }

    @Test
    void invalidAddressIsPermanent() {
        assertThat(MailFailure.isTransient(new MailParseException("invalid address"))).isFalse();
        assertThat(MailFailure.isTransient(
            new MailSendException("failed", new AddressException("bad local"))
        )).isFalse();
    }

    @Test
    void smtpHardBounceIsPermanent() {
        assertThat(MailFailure.isTransient(
            new MailSendException("failed", new SendFailedException("550 5.1.1 user unknown"))
        )).isFalse();
    }

    @Test
    void smtp550InMessageIsPermanent() {
        assertThat(MailFailure.isTransient(new MailSendException("550 mailbox unavailable")))
            .isFalse();
    }
}
