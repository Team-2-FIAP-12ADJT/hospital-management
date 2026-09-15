package com.fiap.hospital.notification.notifications.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fiap.hospital.notification.notifications.domain.Notification;
import com.fiap.hospital.notification.notifications.domain.NotificationStatus;
import com.fiap.hospital.notification.notifications.domain.TerminalReason;
import com.fiap.hospital.notification.notifications.repository.NotificationRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Este recorder só roda quando a entrega lançou exceção INESPERADA: a transitória
 * tratada nunca escapa do {@link NotificationDelivery}. Por isso o motivo gravado
 * aqui é {@code POISONED}, e não {@code TRANSIENT_EXHAUSTED} — é a distinção que
 * {@code attempts} sozinho não faz.
 */
@ExtendWith(MockitoExtension.class)
class NotificationFailureRecorderTest {

    private static final Instant NOW = Instant.parse("2026-09-07T12:00:00.000Z");
    private static final short MAX_ATTEMPTS = 3;

    @Mock
    private NotificationRepository notifications;

    @Test
    void poisonedNotificationIsAbandonedWithItsOwnReason() {
        Notification notification = confirmation();
        notification.recordFailedAttempt();
        notification.recordFailedAttempt();
        when(notifications.findById(notification.getId())).thenReturn(Optional.of(notification));

        recorder().recordUnexpectedFailure(notification.getId());

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.ABANDONED);
        assertThat(notification.getTerminalReason())
            .as("falha inesperada é envenenada, não transitória esgotada")
            .isEqualTo(TerminalReason.POISONED);
    }

    @Test
    void belowTheCapItOnlyCountsTheAttempt() {
        Notification notification = confirmation();
        when(notifications.findById(notification.getId())).thenReturn(Optional.of(notification));

        recorder().recordUnexpectedFailure(notification.getId());

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getAttempts()).isEqualTo((short) 1);
        assertThat(notification.getTerminalReason())
            .as("sem estado terminal não há motivo a gravar")
            .isNull();
    }

    @Test
    void aNotificationThatLeftPendingIsUntouched() {
        Notification notification = confirmation();
        notification.markSent(NOW);
        when(notifications.findById(notification.getId())).thenReturn(Optional.of(notification));

        recorder().recordUnexpectedFailure(notification.getId());

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getTerminalReason()).isNull();
    }

    private NotificationFailureRecorder recorder() {
        return new NotificationFailureRecorder(
            notifications,
            new NotificationProperties(
                Duration.ofHours(24), MAX_ATTEMPTS, 2, "no-reply@hospital.test", ZoneId.of("UTC")
            )
        );
    }

    private static Notification confirmation() {
        return Notification.confirmation(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            NOW.plus(Duration.ofDays(3)), "Dra. Helena Prado", "Cardiologia", NOW
        );
    }
}
