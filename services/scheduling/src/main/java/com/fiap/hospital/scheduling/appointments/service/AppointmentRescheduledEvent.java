package com.fiap.hospital.scheduling.appointments.service;

import com.fiap.hospital.scheduling.outbox.MillisecondInstantSerializer;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.annotation.JsonSerialize;

public record AppointmentRescheduledEvent(
    UUID appointmentId,
    UUID patientId,
    UUID doctorId,
    // Sem o horário anterior o notification não sabe qual lembrete pendente cancelar.
    @JsonSerialize(using = MillisecondInstantSerializer.class) Instant previousScheduledAt,
    @JsonSerialize(using = MillisecondInstantSerializer.class) Instant scheduledAt,
    String status,
    boolean fitIn,
    String fitInReason,
    String patientName,
    String doctorName,
    String doctorSpecialty
) {}
