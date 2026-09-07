package com.fiap.hospital.scheduling.appointments.service;

import com.fiap.hospital.scheduling.outbox.MillisecondInstantSerializer;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.annotation.JsonSerialize;

public record AppointmentCompletedEvent(
    UUID appointmentId,
    UUID patientId,
    UUID doctorId,
    @JsonSerialize(using = MillisecondInstantSerializer.class) Instant scheduledAt,
    String status,
    @JsonSerialize(using = MillisecondInstantSerializer.class) Instant completedAt
) {}
