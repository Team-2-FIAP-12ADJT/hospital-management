package com.fiap.hospital.notification.notifications.service;

import java.time.Instant;
import java.util.UUID;

public record ScheduledAppointment(
    UUID eventId,
    UUID appointmentId,
    UUID patientId,
    Instant scheduledAt,
    String doctorName,
    String doctorSpecialty
) {
}
