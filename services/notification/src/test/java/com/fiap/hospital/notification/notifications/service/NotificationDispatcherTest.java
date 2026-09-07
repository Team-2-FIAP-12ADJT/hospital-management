package com.fiap.hospital.notification.notifications.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fiap.hospital.notification.notifications.domain.Notification;
import com.fiap.hospital.notification.notifications.repository.NotificationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    private static final Instant NOW = Instant.parse("2026-09-07T12:00:00.000Z");

    @Mock
    private NotificationRepository notifications;

    @Mock
    private NotificationDelivery delivery;

    @Test
    void asksForDueNotificationsUnderTheAttemptCapAndBatchSize() {
        when(notifications.findDue(any(), anyShort(), any())).thenReturn(List.of());

        dispatcher().sweep();

        ArgumentCaptor<Instant> now = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Short> maxAttempts = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<Limit> limit = ArgumentCaptor.forClass(Limit.class);
        verify(notifications).findDue(now.capture(), maxAttempts.capture(), limit.capture());

        assertThat(now.getValue()).isEqualTo(NOW);
        assertThat(maxAttempts.getValue()).isEqualTo((short) 3);
        assertThat(limit.getValue().max()).isEqualTo(2);
    }

    @Test
    void deliversEachDueNotificationSeparately() {
        Notification first = confirmation();
        Notification second = confirmation();
        when(notifications.findDue(any(), anyShort(), any())).thenReturn(List.of(first, second));

        dispatcher().sweep();

        verify(delivery).deliver(first.getId());
        verify(delivery).deliver(second.getId());
    }

    private NotificationDispatcher dispatcher() {
        return new NotificationDispatcher(
            notifications,
            delivery,
            new NotificationProperties(
                Duration.ofHours(24), (short) 3, 2,
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
}
