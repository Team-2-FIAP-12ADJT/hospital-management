package com.fiap.hospital.scheduling.appointments.service;

import com.fiap.hospital.scheduling.outbox.MillisecondInstantSerializer;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.annotation.JsonSerialize;

/**
 * Consumido apenas pelo history: o lembrete já disparou antes da consulta
 * acontecer, e a conclusão posterior não muda nada nele.
 */
public record AppointmentCompletedEvent(
    UUID appointmentId,
    UUID patientId,
    UUID doctorId,
    @JsonSerialize(using = MillisecondInstantSerializer.class) Instant scheduledAt,
    String status,
    @JsonSerialize(using = MillisecondInstantSerializer.class) Instant completedAt
) {}
