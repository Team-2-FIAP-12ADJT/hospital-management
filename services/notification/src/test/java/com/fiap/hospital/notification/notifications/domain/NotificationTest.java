package com.fiap.hospital.notification.notifications.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationTest {

    private static final Instant NOW = Instant.parse("2026-09-07T12:00:00.000Z");
    private static final Instant SCHEDULED_AT = NOW.plus(Duration.ofDays(3));

    @Test
    void confirmationIsBornPendingAndFiresImmediately() {
        Notification confirmation = confirmation();

        assertThat(confirmation.getKind()).isEqualTo(NotificationKind.CONFIRMATION);
        assertThat(confirmation.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(confirmation.getFireAt()).isEqualTo(NOW);
        assertThat(confirmation.getAttempts()).isZero();
        assertThat(confirmation.getSentAt()).isNull();
    }

    @Test
    void reminderFiresOneLeadTimeBeforeTheAppointment() {
        Instant fireAt = SCHEDULED_AT.minus(Duration.ofHours(24));

        Optional<Notification> reminder = reminder(fireAt);

        assertThat(reminder).isPresent();
        assertThat(reminder.get().getKind()).isEqualTo(NotificationKind.REMINDER);
        assertThat(reminder.get().getFireAt()).isEqualTo(fireAt);
    }

    @Test
    void reminderIsNotCreatedWhenTheLeadTimeAlreadyElapsed() {
        assertThat(reminder(NOW.minusSeconds(1)))
            .as("consulta perto demais do horário não gera lembrete")
            .isEmpty();
    }

    @Test
    void reminderIsNotCreatedWhenTheFireTimeIsExactlyNow() {
        assertThat(reminder(NOW)).isEmpty();
    }

    @Test
    void markSentRecordsTheInstantAndClosesTheNotification() {
        Notification confirmation = confirmation();

        confirmation.markSent(NOW.plusSeconds(2));

        assertThat(confirmation.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(confirmation.getSentAt()).isEqualTo(NOW.plusSeconds(2));
    }

    @Test
    void markSentIsRefusedOnANotificationAlreadySent() {
        Notification confirmation = confirmation();
        confirmation.markSent(NOW);

        assertThatThrownBy(() -> confirmation.markSent(NOW))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failedAttemptsAccumulateWithoutSending() {
        Notification confirmation = confirmation();

        confirmation.recordFailedAttempt();
        confirmation.recordFailedAttempt();

        assertThat(confirmation.getAttempts()).isEqualTo((short) 2);
        assertThat(confirmation.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(confirmation.getSentAt()).isNull();
    }

    @Test
    void instantsAreTruncatedToMilliseconds() {
        Notification confirmation = Notification.confirmation(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            SCHEDULED_AT.plusNanos(123_456), "Dra. Helena Prado", "Cardiologia",
            NOW.plusNanos(999_999)
        );

        assertThat(confirmation.getScheduledAt()).isEqualTo(SCHEDULED_AT);
        assertThat(confirmation.getFireAt()).isEqualTo(NOW);
    }

    @Test
    void doctorNameIsRequired() {
        assertThatThrownBy(() -> Notification.confirmation(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            SCHEDULED_AT, " ", "Cardiologia", NOW
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private static Notification confirmation() {
        return Notification.confirmation(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            SCHEDULED_AT, "Dra. Helena Prado", "Cardiologia", NOW
        );
    }

    private static Optional<Notification> reminder(Instant fireAt) {
        return Notification.reminder(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            SCHEDULED_AT, "Dra. Helena Prado", "Cardiologia", fireAt, NOW
        );
    }
}
