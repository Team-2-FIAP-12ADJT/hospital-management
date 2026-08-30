package com.fiap.hospital.scheduling.participants.api;

import com.fiap.hospital.scheduling.participants.domain.Patient;
import com.fiap.hospital.scheduling.participants.service.PatientRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patients")
@Tag(name = "Patients", description = "Cadastro de pacientes (ADR-0015)")
public class PatientController {

    private final PatientRegistrationService patientRegistrationService;

    public PatientController(
        PatientRegistrationService patientRegistrationService
    ) {
        this.patientRegistrationService = patientRegistrationService;
    }

    @PostMapping
    @Operation(
        summary = "Cadastra um paciente",
        description = "Grava o Patient e publica PatientRegistered na mesma transação (ADR-0012)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Paciente cadastrado"),
        @ApiResponse(responseCode = "400", description = "Payload inválido"),
        @ApiResponse(responseCode = "409", description = "CPF já cadastrado"),
    })
    public ResponseEntity<PatientResponse> register(
        @Valid @RequestBody RegisterPatientRequest request
    ) {
        Patient patient = patientRegistrationService.register(
            request.taxIdentifier(),
            request.name(),
            request.email(),
            request.phone()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(
            new PatientResponse(patient.getId())
        );
    }
}
