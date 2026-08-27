package com.fiap.hospital.scheduling.participants.api;

import com.fiap.hospital.scheduling.participants.domain.Doctor;
import com.fiap.hospital.scheduling.participants.service.DoctorRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/doctors")
@Tag(name = "Doctors", description = "Cadastro de médicos (ADR-0015)")
@SecurityRequirement(name = "bearerAuth")
public class DoctorController {

    private final DoctorRegistrationService doctorRegistrationService;

    public DoctorController(DoctorRegistrationService doctorRegistrationService) {
        this.doctorRegistrationService = doctorRegistrationService;
    }

    // Cadastro de profissional é rota autenticada, nunca pública (ADR-0013): o
    // papel não vem do corpo da requisição, é decidido no service.
    @PostMapping
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Cadastra um médico",
            description = "Grava o Doctor e publica DoctorRegistered na mesma transação (ADR-0012). "
                    + "Exige papel DOCTOR.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Médico cadastrado"),
            @ApiResponse(responseCode = "400", description = "Payload inválido"),
            @ApiResponse(responseCode = "401", description = "Sem token ou token inválido"),
            @ApiResponse(responseCode = "403", description = "Papel diferente de DOCTOR"),
            @ApiResponse(responseCode = "409", description = "CPF ou CRM já cadastrado")
    })
    public ResponseEntity<DoctorResponse> register(@Valid @RequestBody RegisterDoctorRequest request) {
        Doctor doctor = doctorRegistrationService.register(
                request.taxIdentifier(), request.crm(), request.specialty(), request.name(), request.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(new DoctorResponse(doctor.getId()));
    }
}
