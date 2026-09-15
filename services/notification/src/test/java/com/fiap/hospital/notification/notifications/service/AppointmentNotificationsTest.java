package com.fiap.hospital.notification.notifications.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fiap.hospital.notification.idempotency.IdempotencyService;
import com.fiap.hospital.notification.notifications.domain.Notification;
import com.fiap.hospital.notification.notifications.domain.NotificationKind;
import com.fiap.hospital.notification.notifications.domain.NotificationStatus;
import com.fiap.hospital.notification.notifications.repository.NotificationRepository;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AppointmentNotificationsTest {

    private static final Instant NOW = Instant.parse("2026-09-07T12:00:00.000Z");
    private static final Duration LEAD_TIME = Duration.ofHours(24);

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private NotificationRepository notifications;

    @Test
    void schedulesConfirmationNowAndReminderOneLeadTimeBefore() {
        runTheEffect();
        ScheduledAppointment appointment = appointmentAt(NOW.plus(Duration.ofDays(3)));

        service().schedule(appointment);

        List<Notification> saved = savedNotifications(2);
        Notification confirmation = byKind(saved, NotificationKind.CONFIRMATION);
        Notification reminder = byKind(saved, NotificationKind.REMINDER);

        assertThat(confirmation.getFireAt()).isEqualTo(NOW);
        assertThat(confirmation.getPatientId()).isEqualTo(appointment.patientId());
        assertThat(confirmation.getAppointmentId()).isEqualTo(appointment.appointmentId());
        assertThat(reminder.getFireAt()).isEqualTo(appointment.scheduledAt().minus(LEAD_TIME));
        assertThat(reminder.getScheduledAt()).isEqualTo(appointment.scheduledAt());
    }

    @Test
    void schedulesOnlyTheConfirmationWhenTheAppointmentIsInsideTheLeadTime() {
        runTheEffect();

        service().schedule(appointmentAt(NOW.plus(Duration.ofHours(1))));

        List<Notification> saved = savedNotifications(1);
        assertThat(saved.getFirst().getKind())
            .as("consulta dentro da janela do lembrete gera confirmação, não lembrete")
            .isEqualTo(NotificationKind.CONFIRMATION);
    }

    @Test
    void schedulesNothingWhenTheEventWasAlreadyProcessed() {
        service().schedule(appointmentAt(NOW.plus(Duration.ofDays(3))));

        verifyNoInteractions(notifications);
    }

    @Test
    void reschedulingCancelsThePendingReminderBeforeCreatingTheNewOne() {
        runTheEffect();
        UUID appointmentId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        Notification pending = pendingReminder(appointmentId, patientId, NOW.plus(Duration.ofDays(3)));
        when(notifications.findPendingReminder(appointmentId)).thenReturn(Optional.of(pending));

        service().reschedule(new RescheduledAppointment(
            UUID.randomUUID(), appointmentId, patientId,
            NOW.plus(Duration.ofDays(3)), NOW.plus(Duration.ofDays(5)),
            "Dra. Helena Prado", "Cardiologia"
        ));

        assertThat(pending.getStatus())
            .as("o lembrete do horário antigo é cancelado, não deixado pendente")
            .isEqualTo(NotificationStatus.CANCELLED);

        InOrder inOrder = inOrder(notifications);
        inOrder.verify(notifications).save(pending);
        inOrder.verify(notifications).flush();
        inOrder.verify(notifications, org.mockito.Mockito.atLeastOnce())
            .save(org.mockito.ArgumentMatchers.argThat(saved ->
                saved != null && saved.getStatus() == NotificationStatus.PENDING));
    }

    @Test
    void reschedulingAlsoConfirmsTheChangeAndProgramsTheNewReminder() {
        runTheEffect();
        UUID appointmentId = UUID.randomUUID();
        when(notifications.findPendingReminder(appointmentId)).thenReturn(Optional.empty());
        Instant newSlot = NOW.plus(Duration.ofDays(5));

        service().reschedule(new RescheduledAppointment(
            UUID.randomUUID(), appointmentId, UUID.randomUUID(),
            NOW.plus(Duration.ofDays(3)), newSlot, "Dra. Helena Prado", "Cardiologia"
        ));

        List<Notification> saved = savedNotifications(2);
        assertThat(byKind(saved, NotificationKind.CONFIRMATION).getScheduledAt())
            .isEqualTo(newSlot);
        assertThat(byKind(saved, NotificationKind.REMINDER).getFireAt())
            .isEqualTo(newSlot.minus(LEAD_TIME));
    }

    @Test
    void cancellingCancelsThePendingReminderAndNotifiesNothingElse() {
        runTheEffect();
        UUID appointmentId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        Notification pending = pendingReminder(appointmentId, patientId, NOW.plus(Duration.ofDays(3)));
        when(notifications.findPendingReminder(appointmentId)).thenReturn(Optional.of(pending));

        service().cancel(new CancelledAppointment(UUID.randomUUID(), appointmentId, patientId));

        assertThat(pending.getStatus()).isEqualTo(NotificationStatus.CANCELLED);
        verify(notifications).save(pending);
        org.mockito.Mockito.verifyNoMoreInteractions(
            org.mockito.Mockito.ignoreStubs(notifications)
        );
    }

    @Test
    void cancellingAnAppointmentWhoseReminderAlreadyWentOutIsSilent() {
        runTheEffect();
        UUID appointmentId = UUID.randomUUID();
        when(notifications.findPendingReminder(appointmentId)).thenReturn(Optional.empty());

        service().cancel(new CancelledAppointment(
            UUID.randomUUID(), appointmentId, UUID.randomUUID()
        ));

        verify(notifications, never()).save(any());
    }

    @Test
    void aRepeatedCancellationEventChangesNothing() {
        service().cancel(new CancelledAppointment(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()
        ));

        verifyNoInteractions(notifications);
    }

    private static Notification pendingReminder(UUID appointmentId, UUID patientId, Instant scheduledAt) {
        return Notification.reminder(
            UUID.randomUUID(), patientId, appointmentId, scheduledAt,
            "Dra. Helena Prado", "Cardiologia", scheduledAt.minus(LEAD_TIME), NOW
        ).orElseThrow();
    }

    private AppointmentNotifications service() {
        return new AppointmentNotifications(
            idempotencyService,
            notifications,
            new NotificationProperties(
                LEAD_TIME, (short) 10, 50, "nao-responda@hospital.local", ZoneId.from(ZoneOffset.UTC)
            ),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private void runTheEffect() {
        doAnswer(invocation -> {
            invocation.getArgument(2, Runnable.class).run();
            return null;
        }).when(idempotencyService).process(eq(AppointmentNotifications.CONSUMER), any(), any());
    }

    private List<Notification> savedNotifications(int expected) {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications, org.mockito.Mockito.times(expected)).save(captor.capture());
        return captor.getAllValues();
    }

    private static Notification byKind(List<Notification> saved, NotificationKind kind) {
        return saved.stream()
            .filter(notification -> notification.getKind() == kind)
            .findFirst()
            .orElseThrow(() -> new AssertionError("nenhuma notificação do tipo " + kind));
    }

    private static ScheduledAppointment appointmentAt(Instant scheduledAt) {
        return new ScheduledAppointment(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            scheduledAt, "Dra. Helena Prado", "Cardiologia"
        );
    }
}
