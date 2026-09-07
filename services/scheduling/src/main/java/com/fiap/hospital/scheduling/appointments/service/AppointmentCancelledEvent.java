package com.fiap.hospital.scheduling.appointments.service;

import com.fiap.hospital.scheduling.outbox.MillisecondInstantSerializer;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.annotation.JsonSerialize;

/**
 * Sem dados de exibição: a projeção já materializou a consulta no
 * AppointmentScheduled e aqui só muda o estado.
 */
public record AppointmentCancelledEvent(
    UUID appointmentId,
    UUID patientId,
    UUID doctorId,
    @JsonSerialize(using = MillisecondInstantSerializer.class) Instant scheduledAt,
    String status,
    // Instante do cancelamento, não o da consulta.
    @JsonSerialize(using = MillisecondInstantSerializer.class) Instant cancelledAt
) {}
