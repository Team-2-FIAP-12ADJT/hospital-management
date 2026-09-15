package com.fiap.hospital.notification.notifications.service;

import java.time.Instant;
import java.util.UUID;

public record RescheduledAppointment(
    UUID eventId,
    UUID appointmentId,
    UUID patientId,
    Instant previousScheduledAt,
    Instant scheduledAt,
    String doctorName,
    String doctorSpecialty
) implements AppointmentEvent {
}
