package com.fiap.hospital.scheduling.appointments.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record RescheduleAppointmentRequest(
    @Schema(description = "Novo horário, no futuro", example = "2030-01-01T13:30:00.000Z")
    @NotNull
    Instant scheduledAt,

    @Schema(description = "Encaixe autoriza o conflito de agenda, com justificativa")
    boolean fitIn,

    @Size(max = 255) String fitInReason
) {}
