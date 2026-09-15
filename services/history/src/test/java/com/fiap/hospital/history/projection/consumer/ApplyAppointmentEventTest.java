package com.fiap.hospital.history.projection.consumer;

import com.fiap.hospital.history.projection.domain.AppointmentProjection;
import com.fiap.hospital.history.projection.domain.AppointmentStatus;
import com.fiap.hospital.history.projection.repository.AppointmentProjectionRepository;
import com.fiap.hospital.history.projection.repository.ProjectionFreshnessRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplyAppointmentEventTest {

    @Mock
    private AppointmentProjectionRepository appointments;

    @Mock
    private ProjectionFreshnessRepository freshness;

    @Mock
    private IdempotencyService idempotency;

    @Test
    void insertsNewProjectionInsideIdempotentEffect() {
        AppointmentScheduledMessage message = message(false, null);
        when(appointments.findById(message.appointmentId())).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return null;
        }).when(idempotency).process(org.mockito.ArgumentMatchers.eq(message.eventId()), org.mockito.ArgumentMatchers.any());

        new ApplyAppointmentEvent(appointments, freshness, idempotency).apply(message);

        ArgumentCaptor<AppointmentProjection> saved = ArgumentCaptor.forClass(AppointmentProjection.class);
        verify(appointments).save(saved.capture());
        AppointmentProjection row = saved.getValue();
        assertEquals(message.appointmentId(), row.getAppointmentId());
        assertEquals(AppointmentStatus.SCHEDULED, row.getStatus());
        assertEquals("Ana Ribeiro", row.getPatientName());
        verify(freshness).markApplied(message.occurredAt());
    }

    @Test
    void updatesExistingProjection() {
        AppointmentScheduledMessage message = message(true, "encaixe");
        AppointmentProjection existing = new AppointmentProjection();
        when(appointments.findById(message.appointmentId())).thenReturn(Optional.of(existing));
        doAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return null;
        }).when(idempotency).process(org.mockito.ArgumentMatchers.eq(message.eventId()), org.mockito.ArgumentMatchers.any());

        new ApplyAppointmentEvent(appointments, freshness, idempotency).apply(message);

        ArgumentCaptor<AppointmentProjection> saved = ArgumentCaptor.forClass(AppointmentProjection.class);
        verify(appointments).save(saved.capture());
        assertSame(existing, saved.getValue());
        assertTrue(existing.isFitIn());
        assertEquals("encaixe", existing.getFitInReason());
    }

    @Test
    void dropsOutOfOrderEvents() {
        AppointmentScheduledMessage firstMessage = message(false, null);
        
        AppointmentProjection existing = new AppointmentProjection();
        existing.applyScheduled(
                firstMessage.appointmentId(),
                firstMessage.patientId(),
                firstMessage.doctorId(),
                firstMessage.scheduledAt(),
                firstMessage.fitIn(),
                firstMessage.fitInReason(),
                firstMessage.patientName(),
                firstMessage.doctorName(),
                firstMessage.doctorSpecialty(),
                Instant.parse("2026-09-02T14:00:00.000Z") // newer appliedAt
        );
        
        when(appointments.findById(firstMessage.appointmentId())).thenReturn(Optional.of(existing));
        doAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return null;
        }).when(idempotency).process(org.mockito.ArgumentMatchers.eq(firstMessage.eventId()), org.mockito.ArgumentMatchers.any());

        new ApplyAppointmentEvent(appointments, freshness, idempotency).apply(firstMessage); // occurredAt is 13:30 (older)

        ArgumentCaptor<AppointmentProjection> saved = ArgumentCaptor.forClass(AppointmentProjection.class);
        verify(appointments).save(saved.capture());
        // State should not change, because the event is older than the existing row's updatedAt
        assertEquals(AppointmentStatus.SCHEDULED, saved.getValue().getStatus());
        verify(freshness).markApplied(firstMessage.occurredAt());
        
        // Ensure state wasn't affected (we applied an event that should be dropped by domain logic)
        // Note: applyScheduled updates properties; if it dropped, those properties would remain unchanged.
        // We test this by using a cancelled message with an older timestamp.
        
        AppointmentCancelledMessage olderCancel = new AppointmentCancelledMessage(
                UUID.randomUUID(),
                Instant.parse("2026-09-02T13:45:00.000Z"), // older than existing 14:00
                firstMessage.appointmentId(),
                firstMessage.patientId(),
                firstMessage.doctorId(),
                firstMessage.scheduledAt(),
                Instant.parse("2026-09-02T13:45:00.000Z")
        );
        
        when(appointments.findById(olderCancel.appointmentId())).thenReturn(Optional.of(existing));
        doAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return null;
        }).when(idempotency).process(org.mockito.ArgumentMatchers.eq(olderCancel.eventId()), org.mockito.ArgumentMatchers.any());
        
        new ApplyAppointmentEvent(appointments, freshness, idempotency).apply(olderCancel);
        
        // Still SCHEDULED, not CANCELLED, because olderCancel was dropped.
        assertEquals(AppointmentStatus.SCHEDULED, existing.getStatus());
    }

    private static AppointmentScheduledMessage message(boolean fitIn, String reason) {
        return new AppointmentScheduledMessage(
                UUID.fromString("0b7f4e2a-9c3d-4a1e-8f55-2b6d1c9e7a04"),
                Instant.parse("2026-09-02T13:30:00.000Z"),
                UUID.fromString("7c1e5a93-2f84-4b60-8d17-a3e9c0524b6f"),
                UUID.fromString("3f2b8c10-5d47-4e91-9a2e-7c6f1b0d8e33"),
                UUID.fromString("b91c4d72-8a05-4f36-b1de-0e5a72c4f118"),
                Instant.parse("2026-09-02T13:30:00.000Z"),
                fitIn,
                reason,
                "Ana Ribeiro",
                "Dr. Paulo Menezes",
                "Cardiologia"
        );
    }
    @Test
    void appliesRescheduledMessage() {
        AppointmentRescheduledMessage message = new AppointmentRescheduledMessage(
                UUID.randomUUID(), Instant.parse("2026-07-09T10:00:00Z"),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Instant.parse("2026-07-10T14:00:00Z"), Instant.parse("2026-07-11T14:00:00Z"),
                false, null, "P", "D", "S"
        );
        when(appointments.findById(message.appointmentId())).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return null;
        }).when(idempotency).process(org.mockito.ArgumentMatchers.eq(message.eventId()), org.mockito.ArgumentMatchers.any());
        new ApplyAppointmentEvent(appointments, freshness, idempotency).apply(message);
        ArgumentCaptor<AppointmentProjection> captor = ArgumentCaptor.forClass(AppointmentProjection.class);
        verify(appointments).save(captor.capture());
        assertEquals(Instant.parse("2026-07-11T14:00:00Z"), captor.getValue().getScheduledAt());
    }

    @Test
    void appliesCancelledMessage() {
        AppointmentCancelledMessage message = new AppointmentCancelledMessage(
                UUID.randomUUID(), Instant.parse("2026-07-09T10:00:00Z"),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Instant.parse("2026-07-11T14:00:00Z"), Instant.parse("2026-07-09T11:00:00Z")
        );
        when(appointments.findById(message.appointmentId())).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return null;
        }).when(idempotency).process(org.mockito.ArgumentMatchers.eq(message.eventId()), org.mockito.ArgumentMatchers.any());
        new ApplyAppointmentEvent(appointments, freshness, idempotency).apply(message);
        ArgumentCaptor<AppointmentProjection> captor = ArgumentCaptor.forClass(AppointmentProjection.class);
        verify(appointments).save(captor.capture());
        assertEquals(Instant.parse("2026-07-09T11:00:00Z"), captor.getValue().getCancelledAt());
    }

    @Test
    void appliesCompletedMessage() {
        AppointmentCompletedMessage message = new AppointmentCompletedMessage(
                UUID.randomUUID(), Instant.parse("2026-07-09T10:00:00Z"),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Instant.parse("2026-07-11T14:00:00Z"), Instant.parse("2026-07-09T11:00:00Z")
        );
        when(appointments.findById(message.appointmentId())).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return null;
        }).when(idempotency).process(org.mockito.ArgumentMatchers.eq(message.eventId()), org.mockito.ArgumentMatchers.any());
        new ApplyAppointmentEvent(appointments, freshness, idempotency).apply(message);
        ArgumentCaptor<AppointmentProjection> captor = ArgumentCaptor.forClass(AppointmentProjection.class);
        verify(appointments).save(captor.capture());
        assertEquals(Instant.parse("2026-07-09T11:00:00Z"), captor.getValue().getCompletedAt());
    }
}
