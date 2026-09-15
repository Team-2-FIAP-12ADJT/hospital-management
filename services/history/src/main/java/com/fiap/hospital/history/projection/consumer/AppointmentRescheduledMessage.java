package com.fiap.hospital.history.projection.consumer;

import java.time.Instant;
import java.util.UUID;

record AppointmentRescheduledMessage(
        UUID eventId,
        Instant occurredAt,
        UUID appointmentId,
        UUID patientId,
        UUID doctorId,
        Instant previousScheduledAt,
        Instant scheduledAt,
        boolean fitIn,
        String fitInReason,
        String patientName,
        String doctorName,
        String doctorSpecialty
) implements AppointmentEvent {}
