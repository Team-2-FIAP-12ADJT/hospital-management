package com.fiap.hospital.scheduling.participants.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RegisterDoctorRequest(

        @Schema(description = "CPF, sem pontuação", example = "39053344705")
        @NotBlank
        @Pattern(regexp = "\\d{11}", message = "must be 11 digits")
        String taxIdentifier,

        @Schema(description = "Registro profissional", example = "CRM-SP 123456")
        @NotBlank
        String crm,

        @Schema(example = "Cardiologia")
        @NotBlank
        String specialty,

        @Schema(example = "Dra. Helena Prado")
        @NotBlank
        String name,

        @Schema(example = "helena.prado@hospital.local")
        @NotBlank
        @Email
        String email) {
}
