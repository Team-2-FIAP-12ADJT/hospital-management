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
class ApplyAppointmentScheduledTest {

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

        new ApplyAppointmentScheduled(appointments, freshness, idempotency).apply(message);

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

        new ApplyAppointmentScheduled(appointments, freshness, idempotency).apply(message);

        ArgumentCaptor<AppointmentProjection> saved = ArgumentCaptor.forClass(AppointmentProjection.class);
        verify(appointments).save(saved.capture());
        assertSame(existing, saved.getValue());
        assertTrue(existing.isFitIn());
        assertEquals("encaixe", existing.getFitInReason());
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
}
