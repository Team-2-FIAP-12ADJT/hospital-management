package com.fiap.hospital.history.projection.consumer;

import java.time.Instant;
import java.util.UUID;

record AppointmentScheduledMessage(
        UUID eventId,
        Instant occurredAt,
        UUID appointmentId,
        UUID patientId,
        UUID doctorId,
        Instant scheduledAt,
        boolean fitIn,
        String fitInReason,
        String patientName,
        String doctorName,
        String doctorSpecialty
) {
}
