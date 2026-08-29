package com.fiap.hospital.scheduling.participants.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record DoctorResponse(
    @Schema(
        description = "Mesmo id que o identity usa para o User provisionado (ADR-0015)"
    )
    UUID id
) {}
