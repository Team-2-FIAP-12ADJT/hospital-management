package com.fiap.hospital.notification.notifications.service;

import static org.assertj.core.api.Assertions.assertThat;
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

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryTest {

    private static final Instant NOW = Instant.parse("2026-09-07T12:00:00.000Z");
    private static final short MAX_ATTEMPTS = 3;

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
    void countsTheAttemptAndKeepsPendingWhenSendingFails() throws Exception {
        Notification notification = confirmation();
        when(notifications.findById(notification.getId())).thenReturn(Optional.of(notification));
        when(contacts.findById(notification.getPatientId()))
            .thenReturn(Optional.of(contactWith("marcos@exemplo.com")));
        doThrow(new IllegalStateException("smtp down")).when(mailer).send(any(), anyString());

        delivery().deliver(notification.getId());

        assertThat(notification.getStatus())
            .as("falha de envio não consome a notificação — a varredura tenta de novo")
            .isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getAttempts()).isEqualTo((short) 1);
        assertThat(notification.getSentAt()).isNull();
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
    void stopsRetryingOnceTheAttemptCapIsReached() {
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
            .as("o teto não muda o estado — a linha segue PENDING e visível")
            .isEqualTo(NotificationStatus.PENDING);
    }

    @Test
    void abandoningANotificationIsLoggedAtErrorBecauseTheStateDoesNotChange() {
        Notification notification = confirmation();
        notification.recordFailedAttempt();
        notification.recordFailedAttempt();
        when(notifications.findById(notification.getId())).thenReturn(Optional.of(notification));
        when(contacts.findById(notification.getPatientId())).thenReturn(Optional.empty());

        try (CapturedLog captured = CapturedLog.of(NotificationDelivery.class)) {
            delivery().deliver(notification.getId());

            assertThat(captured.events())
                .as("sem ERROR no esgotamento, abandonada e enfileirada ficam idênticas")
                .anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                    assertThat(event.getFormattedMessage()).contains("giving up on notification");
                });
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
