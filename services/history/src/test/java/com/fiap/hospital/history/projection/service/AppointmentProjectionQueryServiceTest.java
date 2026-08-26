package com.fiap.hospital.history.projection.service;

import com.fiap.hospital.history.projection.domain.AppointmentProjection;
import com.fiap.hospital.history.projection.domain.AppointmentStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.util.ReflectionTestUtils.setField;

class AppointmentProjectionQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-25T12:00:00Z");

    @Test
    void upcomingRequiresScheduledStatusAndTimeNotBeforeNow() {
        assertTrue(AppointmentProjectionQueryService.isUpcoming(row(AppointmentStatus.SCHEDULED, "2026-08-25T12:00:00Z"), NOW));
        assertTrue(AppointmentProjectionQueryService.isUpcoming(row(AppointmentStatus.SCHEDULED, "2026-09-15T13:30:00Z"), NOW));
        assertFalse(AppointmentProjectionQueryService.isUpcoming(row(AppointmentStatus.SCHEDULED, "2026-08-01T10:00:00Z"), NOW));
        assertFalse(AppointmentProjectionQueryService.isUpcoming(row(AppointmentStatus.CANCELLED, "2026-09-05T09:00:00Z"), NOW));
        assertFalse(AppointmentProjectionQueryService.isUpcoming(row(AppointmentStatus.COMPLETED, "2026-07-10T14:00:00Z"), NOW));
    }

    private static AppointmentProjection row(AppointmentStatus status, String scheduledAt) {
        AppointmentProjection projection = new AppointmentProjection();
        setField(projection, "status", status);
        setField(projection, "scheduledAt", Instant.parse(scheduledAt));
        return projection;
    }
}
