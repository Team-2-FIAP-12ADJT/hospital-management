package com.fiap.hospital.scheduling.appointments.service;

import com.fiap.hospital.scheduling.outbox.MillisecondInstantSerializer;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.annotation.JsonSerialize;

public record AppointmentScheduledEvent(
    UUID appointmentId,
    UUID patientId,
    UUID doctorId,
    // Tres casas sempre, inclusive .000, evitam a largura variavel do Instant padrao.
    @JsonSerialize(using = MillisecondInstantSerializer.class) Instant scheduledAt,
    // A projecao faz upsert direto, sem traduzir eventType em estado.
    String status,
    boolean fitIn,
    String fitInReason,
    String patientName,
    String doctorName,
    String doctorSpecialty
) {}
