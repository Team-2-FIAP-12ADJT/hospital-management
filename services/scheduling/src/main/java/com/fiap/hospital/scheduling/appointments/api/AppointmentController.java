package com.fiap.hospital.scheduling.appointments.api;

import com.fiap.hospital.scheduling.appointments.domain.Appointment;
import com.fiap.hospital.scheduling.appointments.service.AppointmentSchedulingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/appointments")
@Tag(name = "Appointments", description = "Agendamento de consultas")
@SecurityRequirement(name = "bearerAuth")
public class AppointmentController {

    private final AppointmentSchedulingService service;

    public AppointmentController(AppointmentSchedulingService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE')")
    @Operation(summary = "Agenda uma consulta")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Consulta agendada"),
        @ApiResponse(responseCode = "400", description = "Payload, horário ou participante inválido"),
        @ApiResponse(responseCode = "401", description = "Sem token ou token inválido"),
        @ApiResponse(responseCode = "403", description = "Papel não autorizado"),
        @ApiResponse(responseCode = "409", description = "Horário ocupado ou transição inválida")
    })
    public ResponseEntity<AppointmentResponse> schedule(
        @Valid @RequestBody ScheduleAppointmentRequest request
    ) {
        Appointment appointment = service.schedule(
            request.patientId(),
            request.doctorId(),
            request.scheduledAt(),
            request.fitIn(),
            request.fitInReason()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new AppointmentResponse(appointment.getId()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Void> invalidDomainInput() {
        return ResponseEntity.badRequest().build();
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Void> invalidDomainState() {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Void> responseStatus(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).build();
    }
}
