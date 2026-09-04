package com.fiap.hospital.history.projection.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.util.ReflectionTestUtils.setField;

class AppointmentProjectionTest {

    @Test
    void gettersExposePersistedFields() {
        UUID appointmentId = UUID.fromString("10000000-0000-4000-8000-000000000003");
        UUID patientId = UUID.fromString("00000000-0000-4000-8000-000000000003");
        UUID doctorId = UUID.fromString("00000000-0000-4000-8000-000000000001");
        Instant scheduledAt = Instant.parse("2026-07-10T14:00:00Z");
        Instant cancelledAt = Instant.parse("2026-07-09T10:00:00Z");
        Instant completedAt = Instant.parse("2026-07-10T14:42:00Z");
        Instant updatedAt = Instant.parse("2026-07-10T14:43:00Z");

        AppointmentProjection projection = new AppointmentProjection();
        setField(projection, "appointmentId", appointmentId);
        setField(projection, "patientId", patientId);
        setField(projection, "doctorId", doctorId);
        setField(projection, "scheduledAt", scheduledAt);
        setField(projection, "status", AppointmentStatus.CANCELLED);
        setField(projection, "fitIn", false);
        setField(projection, "fitInReason", null);
        setField(projection, "patientName", "Marcos Vieira");
        setField(projection, "doctorName", "Dra. Helena Prado");
        setField(projection, "doctorSpecialty", "Cardiologia");
        setField(projection, "cancelledAt", cancelledAt);
        setField(projection, "completedAt", completedAt);
        setField(projection, "updatedAt", updatedAt);

        assertEquals(appointmentId, projection.getAppointmentId());
        assertEquals(patientId, projection.getPatientId());
        assertEquals(doctorId, projection.getDoctorId());
        assertEquals(scheduledAt, projection.getScheduledAt());
        assertEquals(AppointmentStatus.CANCELLED, projection.getStatus());
        assertFalse(projection.isFitIn());
        assertEquals(null, projection.getFitInReason());
        assertEquals("Marcos Vieira", projection.getPatientName());
        assertEquals("Dra. Helena Prado", projection.getDoctorName());
        assertEquals("Cardiologia", projection.getDoctorSpecialty());
        assertEquals(cancelledAt, projection.getCancelledAt());
        assertEquals(completedAt, projection.getCompletedAt());
    }

    @Test
    void statusEnumContainsProjectionValues() {
        assertEquals(3, AppointmentStatus.values().length);
        assertEquals(AppointmentStatus.SCHEDULED, AppointmentStatus.valueOf("SCHEDULED"));
    }
}
