package com.fiap.hospital.scheduling.participants.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterPatientRequest(
    @Schema(description = "CPF, sem pontuação", example = "39053344705")
    @NotBlank
    @Pattern(regexp = "\\d{11}", message = "must be 11 digits")
    String taxIdentifier,

    @Schema(example = "Marcos Vieira") @NotBlank @Size(max = 150) String name,

    @Schema(example = "marcos.vieira@exemplo.com")
    @NotBlank
    @Email
    @Size(max = 150)
    String email,

    @Schema(description = "Telefone opcional", example = "+5511999999999")
    @Size(max = 20)
    String phone
) {}
