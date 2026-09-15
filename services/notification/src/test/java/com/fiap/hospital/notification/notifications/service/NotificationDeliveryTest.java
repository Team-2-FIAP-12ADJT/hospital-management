package com.fiap.hospital.notification.notifications.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fiap.hospital.notification.notifications.domain.ContactReplica;
import com.fiap.hospital.notification.notifications.domain.Notification;
import com.fiap.hospital.notification.notifications.domain.NotificationStatus;
import com.fiap.hospital.notification.notifications.domain.TerminalReason;
import com.fiap.hospital.notification.notifications.repository.ContactReplicaRepository;
import com.fiap.hospital.notification.notifications.repository.NotificationRepository;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryTest {

    private static final Instant NOW = Instant.parse("2026-09-07T12:00:00.000Z");
    private static final short MAX_ATTEMPTS = 3;
    private static final String CONTACT_EMAIL = "paciente.marcador@example.test";

    @Mock
    private NotificationRepository notifications;

    @Mock
    private ContactReplicaRepository contacts;

    @Mock
    private NotificationMailer mailer;

    @Test
    void sendsToTheReplicaAddressAndMarksSent() throws Exception {
        Notification notification = confirmation();
        when(notifications.findById(notification.getId())).thenReturn(Optional.of(notification));
        when(contacts.findById(notification.getPatientId()))
            .thenReturn(Optional.of(contactWith("marcos@exemplo.com")));

        delivery().deliver(notification.getId());

        verify(mailer).send(notification, "marcos@exemplo.com");
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getSentAt()).isEqualTo(NOW);
        verify(notifications).save(notification);
    }

    @Test
    void staysPendingWhenTheContactReplicaHasNotArrivedYet() {
        Notification notification = confirmation();
        when(notifications.findById(notification.getId())).thenReturn(Optional.of(notification));
        when(contacts.findById(notification.getPatientId())).thenReturn(Optional.empty());

        delivery().deliver(notification.getId());

        verifyNoInteractions(mailer);
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getAttempts()).isEqualTo((short) 1);
        verify(notifications).save(notification);
    }

    @Test
    void propagatesUnexpectedSendingFailuresWithoutRecordingAnAttempt() throws Exception {
        Notification notification = confirmation();
        when(notifications.findById(notification.getId())).thenReturn(Optional.of(notification));
        when(contacts.findById(notification.getPatientId()))
            .thenReturn(Optional.of(contactWith("marcos@exemplo.com")));
        doThrow(new IllegalStateException("smtp down")).when(mailer).send(any(), anyString());

        assertThatThrownBy(() -> delivery().deliver(notification.getId()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("smtp down");

        assertThat(notification.getStatus())
            .as("falha inesperada não deve ser tratada como retry de entrega")
            .isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getAttempts()).isZero();
        assertThat(notification.getSentAt()).isNull();
    }

    @Test
    void countsTransientSendingFailuresAndLeavesTheNotificationPending() throws Exception {
        Notification notification = confirmation();
        when(notifications.findById(notification.getId())).thenReturn(Optional.of(notification));
        when(contacts.findById(notification.getPatientId()))
            .thenReturn(Optional.of(contactWith("marcos@exemplo.com")));
        doThrow(new MailSendException("smtp down")).when(mailer).send(any(), anyString());

        delivery().deliver(notification.getId());

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getAttempts()).isEqualTo((short) 1);
        verify(notifications).save(notification);
    }

    @Test
    void marksPermanentSendingFailuresAsFailedWithoutBurningTheAttemptCounter() throws Exception {
        Notification notification = confirmation();
        when(notifications.findById(notification.getId())).thenReturn(Optional.of(notification));
        when(contacts.findById(notification.getPatientId()))
            .thenReturn(Optional.of(contactWith("marcos@exemplo.com")));
        doThrow(new MailParseException("invalid address")).when(mailer).send(any(), anyString());

        delivery().deliver(notification.getId());

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getAttempts()).isZero();
        verify(notifications).save(notification);
    }

    @Test
    void ignoresANotificationThatIsNoLongerPending() {
        Notification notification = confirmation();
        notification.markSent(NOW);
        when(notifications.findById(notification.getId())).thenReturn(Optional.of(notification));

        delivery().deliver(notification.getId());

        verifyNoInteractions(mailer);
        verify(notifications, never()).save(any());
    }

    @Test
    void ignoresANotificationThatDisappeared() {
        UUID missing = UUID.randomUUID();
        when(notifications.findById(missing)).thenReturn(Optional.empty());

        delivery().deliver(missing);

        verifyNoInteractions(mailer, contacts);
    }

    @Test
    void abandonsTransientFailuresOnceTheAttemptCapIsReached() {
        Notification notification = confirmation();
        notification.recordFailedAttempt();
        notification.recordFailedAttempt();
        when(notifications.findById(notification.getId())).thenReturn(Optional.of(notification));
        when(contacts.findById(notification.getPatientId())).thenReturn(Optional.empty());

        delivery().deliver(notification.getId());

        assertThat(notification.getAttempts())
            .as("a terceira tentativa atinge o teto")
            .isEqualTo(MAX_ATTEMPTS);
        assertThat(notification.getStatus())
            .isEqualTo(NotificationStatus.ABANDONED);
    }

    // O estado terminal diz de QUEM e o veredito; o motivo diz QUAL foi. Sem ele,
    // `attempts` no teto nao separa transitoria esgotada de envenenada, e a causa
    // so existiria no log.
    @Test
    void permanentFailureRecordsTheDestinationVerdictAsTheReason() throws Exception {
        Notification notification = confirmation();
        when(notifications.findById(notification.getId())).thenReturn(Optional.of(notification));
        when(contacts.findById(notification.getPatientId()))
            .thenReturn(Optional.of(contactWith(CONTACT_EMAIL)));
        doThrow(new MailSendException("550 rejected")).when(mailer).send(any(), anyString());

        delivery().deliver(notification.getId());

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getTerminalReason())
            .isEqualTo(TerminalReason.PERMANENT_MAIL_FAILURE);
    }

    @Test
    void exhaustedTransientFailureRecordsOurOwnVerdictAsTheReason() {
        Notification notification = confirmation();
        notification.recordFailedAttempt();
        notification.recordFailedAttempt();
        when(notifications.findById(notification.getId())).thenReturn(Optional.of(notification));
        when(contacts.findById(notification.getPatientId())).thenReturn(Optional.empty());

        delivery().deliver(notification.getId());

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.ABANDONED);
        assertThat(notification.getTerminalReason())
            .as("transitoria esgotada, nao envenenada — o caminho envenenado e o do recorder")
            .isEqualTo(TerminalReason.TRANSIENT_EXHAUSTED);
    }

    // Linha acima do teto so existe quando `maxAttempts` baixa com fila existente.
    // Antes ela ficava invisivel para o findDue e encalhava em PENDING para sempre;
    // agora a varredura a alcanca e ela sai do limbo na primeira passada.
    // ⚠ O CONTATO TEM DE EXISTIR aqui: com contato ausente o caminho antigo tambem
    // terminaliza sem tocar no mailer, e o teste passaria com a guarda de teto
    // removida. Com contato valido, so a guarda impede o envio.
    @Test
    void aNotificationAlreadyOverTheCapIsTerminalisedInsteadOfStayingPending() throws Exception {
        Notification notification = confirmation();
        notification.recordFailedAttempt();
        notification.recordFailedAttempt();
        notification.recordFailedAttempt();
        notification.recordFailedAttempt();
        short attemptsBefore = notification.getAttempts();
        when(notifications.findById(notification.getId())).thenReturn(Optional.of(notification));

        delivery().deliver(notification.getId());

        verifyNoInteractions(mailer, contacts);
        assertThat(notification.getAttempts())
            .as("acima do teto nao gasta tentativa: e parada, nao nova tentativa")
            .isEqualTo(attemptsBefore);
        assertThat(notification.getAttempts()).isGreaterThan(MAX_ATTEMPTS);
        assertThat(notification.getStatus())
            .as("acima do teto sai de PENDING em vez de encalhar")
            .isEqualTo(NotificationStatus.ABANDONED);
        assertThat(notification.getTerminalReason()).isEqualTo(TerminalReason.TRANSIENT_EXHAUSTED);
    }

    @Test
    void abandoningANotificationIsLoggedAtError() {
        Notification notification = confirmation();
        notification.recordFailedAttempt();
        notification.recordFailedAttempt();
        when(notifications.findById(notification.getId())).thenReturn(Optional.of(notification));
        when(contacts.findById(notification.getPatientId())).thenReturn(Optional.empty());

        try (CapturedLog captured = CapturedLog.of(NotificationDelivery.class)) {
            delivery().deliver(notification.getId());

            assertThat(captured.events())
                .as("o abandono precisa ficar visível no log")
                .anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                    assertThat(event.getFormattedMessage()).contains("abandoning notification");
                });
        }
    }

    // A mensagem do SMTP carrega o endereço numa rejeição típica ("550 <email>
    // rejected"). O log tem de sair com a classificação e o código de resposta, e
    // sem o texto do servidor — nos DOIS caminhos, permanente e transitório.
    @Test
    void permanentMailFailureIsLoggedWithoutTheServerMessage() throws Exception {
        Notification notification = confirmation();
        when(notifications.findById(notification.getId())).thenReturn(Optional.of(notification));
        when(contacts.findById(notification.getPatientId()))
            .thenReturn(Optional.of(contactWith(CONTACT_EMAIL)));
        doThrow(new MailSendException("550 " + CONTACT_EMAIL + " rejected"))
            .when(mailer).send(any(), anyString());

        try (CapturedLog captured = CapturedLog.of(NotificationDelivery.class)) {
            delivery().deliver(notification.getId());

            assertThat(captured.events())
                .anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                    assertThat(event.getFormattedMessage()).contains("smtpReply=550");
                });
            assertThat(captured.events())
                .allSatisfy(event ->
                    assertThat(event.getFormattedMessage()).doesNotContain(CONTACT_EMAIL));
        }
    }

    @Test
    void transientMailFailureIsLoggedWithoutTheServerMessage() throws Exception {
        Notification notification = confirmation();
        when(notifications.findById(notification.getId())).thenReturn(Optional.of(notification));
        when(contacts.findById(notification.getPatientId()))
            .thenReturn(Optional.of(contactWith(CONTACT_EMAIL)));
        doThrow(new MailSendException("421 " + CONTACT_EMAIL + " try again later"))
            .when(mailer).send(any(), anyString());

        try (CapturedLog captured = CapturedLog.of(NotificationDelivery.class)) {
            delivery().deliver(notification.getId());

            assertThat(captured.events())
                .anySatisfy(event ->
                    assertThat(event.getFormattedMessage()).contains("smtpReply=421"));
            assertThat(captured.events())
                .allSatisfy(event ->
                    assertThat(event.getFormattedMessage()).doesNotContain(CONTACT_EMAIL));
        }
    }

    @Test
    void aRetryBelowTheCapIsLoggedAtWarnAndNotError() {
        Notification notification = confirmation();
        when(notifications.findById(notification.getId())).thenReturn(Optional.of(notification));
        when(contacts.findById(notification.getPatientId())).thenReturn(Optional.empty());

        try (CapturedLog captured = CapturedLog.of(NotificationDelivery.class)) {
            delivery().deliver(notification.getId());

            assertThat(captured.events())
                .noneSatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.ERROR));
            assertThat(captured.events())
                .anySatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
        }
    }

    private record CapturedLog(Logger logger, ListAppender<ILoggingEvent> appender)
        implements AutoCloseable {

        static CapturedLog of(Class<?> type) {
            ListAppender<ILoggingEvent> appender = new ListAppender<>();
            appender.start();
            Logger logger = (Logger) LoggerFactory.getLogger(type);
            logger.addAppender(appender);
            return new CapturedLog(logger, appender);
        }

        List<ILoggingEvent> events() {
            return appender.list;
        }

        @Override
        public void close() {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private NotificationDelivery delivery() {
        return new NotificationDelivery(
            notifications,
            contacts,
            mailer,
            new NotificationProperties(
                Duration.ofHours(24), MAX_ATTEMPTS, 50,
                "nao-responda@hospital.local", ZoneId.from(ZoneOffset.UTC)
            ),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static Notification confirmation() {
        return Notification.confirmation(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            NOW.plus(Duration.ofDays(3)), "Dra. Helena Prado", "Cardiologia", NOW
        );
    }

    private static ContactReplica contactWith(String email) throws Exception {
        ContactReplica replica = newContactReplica();
        Field field = ContactReplica.class.getDeclaredField("email");
        field.setAccessible(true);
        field.set(replica, email);
        return replica;
    }

    private static ContactReplica newContactReplica() throws Exception {
        var constructor = ContactReplica.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }
}
