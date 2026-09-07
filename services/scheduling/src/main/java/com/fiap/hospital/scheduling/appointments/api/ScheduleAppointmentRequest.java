package com.fiap.hospital.scheduling.appointments.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record ScheduleAppointmentRequest(
    @NotNull UUID patientId,
    @NotNull UUID doctorId,
    @NotNull Instant scheduledAt,
    boolean fitIn,
    @Size(max = 255) String fitInReason
) {}
