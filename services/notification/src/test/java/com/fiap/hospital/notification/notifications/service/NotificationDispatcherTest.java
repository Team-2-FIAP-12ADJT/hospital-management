package com.fiap.hospital.notification.notifications.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
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

    @Mock
    private NotificationFailureRecorder failureRecorder;

    // O teto de tentativas saiu da consulta: quem decide elegibilidade e o ESTADO,
    // e a linha que esgota o teto sai de PENDING na mesma varredura. Filtrar por
    // tentativa aqui deixava linha encalhada em PENDING quando maxAttempts baixava.
    @Test
    void asksForDueNotificationsByStateAndBatchSizeOnly() {
        when(notifications.findDue(any(), any())).thenReturn(List.of());

        dispatcher().sweep();

        ArgumentCaptor<Instant> now = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Limit> limit = ArgumentCaptor.forClass(Limit.class);
        verify(notifications).findDue(now.capture(), limit.capture());

        assertThat(now.getValue()).isEqualTo(NOW);
        assertThat(limit.getValue().max()).isEqualTo(2);
    }

    @Test
    void deliversEachDueNotificationSeparately() {
        Notification first = confirmation();
        Notification second = confirmation();
        when(notifications.findDue(any(), any())).thenReturn(List.of(first, second));

        dispatcher().sweep();

        verify(delivery).deliver(first.getId());
        verify(delivery).deliver(second.getId());
    }

    @Test
    void continuesDeliveringTheBatchWhenOneNotificationHasAnUnexpectedFailure() {
        Notification first = confirmation();
        Notification second = confirmation();
        when(notifications.findDue(any(), any())).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("mailer exploded")).when(delivery).deliver(first.getId());

        dispatcher().sweep();

        verify(delivery).deliver(first.getId());
        verify(delivery).deliver(second.getId());
        verify(failureRecorder).recordUnexpectedFailure(first.getId());
    }

    @Test
    void retriesThePoisonedNotificationOnTheNextSweepWithoutBlockingTheBatch() {
        Notification first = confirmation();
        Notification second = confirmation();
        when(notifications.findDue(any(), any())).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("mailer exploded")).when(delivery).deliver(first.getId());

        dispatcher().sweep();
        dispatcher().sweep();

        verify(delivery, times(2)).deliver(first.getId());
        verify(delivery, times(2)).deliver(second.getId());
        verify(failureRecorder, times(2)).recordUnexpectedFailure(first.getId());
    }

    @Test
    void continuesDeliveringTheBatchWhenFailureRecordingFails() {
        Notification first = confirmation();
        Notification second = confirmation();
        when(notifications.findDue(any(), any())).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("mailer exploded")).when(delivery).deliver(first.getId());
        doThrow(new IllegalStateException("database unavailable"))
            .when(failureRecorder).recordUnexpectedFailure(first.getId());

        dispatcher().sweep();

        verify(delivery).deliver(first.getId());
        verify(failureRecorder).recordUnexpectedFailure(first.getId());
        verify(delivery).deliver(second.getId());
    }

    private NotificationDispatcher dispatcher() {
        return dispatcher(delivery);
    }

    private NotificationDispatcher dispatcher(NotificationDelivery notificationDelivery) {
        return new NotificationDispatcher(
            notifications,
            notificationDelivery,
            failureRecorder,
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
