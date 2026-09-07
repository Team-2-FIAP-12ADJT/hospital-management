package com.fiap.hospital.notification.notifications.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fiap.hospital.notification.notifications.domain.ContactReplica;
import com.fiap.hospital.notification.notifications.domain.Notification;
import com.fiap.hospital.notification.notifications.domain.NotificationStatus;
import com.fiap.hospital.notification.notifications.repository.ContactReplicaRepository;
import com.fiap.hospital.notification.notifications.repository.NotificationRepository;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryTest {

    private static final Instant NOW = Instant.parse("2026-09-07T12:00:00.000Z");

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
            .as("falha de envio não consome o aviso — a varredura tenta de novo")
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

    private NotificationDelivery delivery() {
        return new NotificationDelivery(
            notifications, contacts, mailer, Clock.fixed(NOW, ZoneOffset.UTC)
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
