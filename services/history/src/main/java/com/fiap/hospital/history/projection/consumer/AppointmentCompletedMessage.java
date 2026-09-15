package com.fiap.hospital.history.projection.consumer;

import java.time.Instant;
import java.util.UUID;

record AppointmentCompletedMessage(
        UUID eventId,
        Instant occurredAt,
        UUID appointmentId,
        UUID patientId,
        UUID doctorId,
        Instant scheduledAt,
        Instant completedAt
) implements AppointmentEvent {}
