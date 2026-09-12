package com.fiap.hospital.notification.invite;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.mail.Address;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
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

    @Test
    void smtp450GreylistingIsTransient() {
        assertThat(MailFailure.isTransient(
            new MailSendException("failed", new SendFailedException("450 4.2.0 greylisted, try again later"))
        )).isTrue();
    }

    @Test
    void smtp451And452AreTransient() {
        assertThat(MailFailure.isTransient(
            new MailSendException("failed", new SendFailedException("451 4.3.0 local error"))
        )).isTrue();
        assertThat(MailFailure.isTransient(
            new MailSendException("failed", new SendFailedException("452 4.2.2 mailbox busy"))
        )).isTrue();
    }

    @Test
    void sendFailedExceptionWithInvalidAddressesIsPermanentEvenWithoutReplyCode() throws Exception {
        SendFailedException sendFailed = new SendFailedException(
            "rejected",
            null,
            new Address[0],
            new Address[0],
            new Address[] {new InternetAddress("bad@example.com")}
        );

        assertThat(MailFailure.isTransient(new MailSendException("failed", sendFailed))).isFalse();
    }

    @Test
    void sendFailedExceptionWithoutRecognizableReplyCodeIsTransient() {
        assertThat(MailFailure.isTransient(
            new MailSendException("failed", new SendFailedException("connection dropped mid transaction"))
        )).isTrue();
    }

    @Test
    void hardBounce550DoesNotMatchAsSubstringOfExtendedCode() {
        assertThat(MailFailure.isTransient(
            new MailSendException("failed", new SendFailedException("550-5.1.1 user unknown"))
        )).isFalse();
    }

    @Test
    void connectionFailureMentioningPortNumberIsTransientNotMatchedAsReplyCode() {
        assertThat(MailFailure.isTransient(
            new MailSendException("Couldn't connect to SMTP host: smtp.example.com, port: 587")
        )).isTrue();
    }

    @Test
    void hardBounceMentioningPortNumberInMessageIsStillPermanent() {
        assertThat(MailFailure.isTransient(
            new MailSendException("failed", new SendFailedException("550 relay refused, port 465"))
        )).isFalse();
    }
}
