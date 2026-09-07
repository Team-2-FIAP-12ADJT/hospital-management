package com.fiap.hospital.scheduling.appointments.api;

import com.fiap.hospital.scheduling.appointments.domain.Appointment;
import com.fiap.hospital.scheduling.appointments.service.AppointmentSchedulingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
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

    // O enunciado dá ao enfermeiro o verbo de registrar consulta e ao médico o de
    // editá-la; as três transições abaixo são a edição.
    @PostMapping("/{id}/reschedule")
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Remarca uma consulta")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Consulta remarcada"),
        @ApiResponse(responseCode = "400", description = "Payload ou horário inválido"),
        @ApiResponse(responseCode = "401", description = "Sem token ou token inválido"),
        @ApiResponse(responseCode = "403", description = "Papel não autorizado"),
        @ApiResponse(responseCode = "404", description = "Consulta inexistente"),
        @ApiResponse(responseCode = "409", description = "Horário ocupado, consulta já iniciada ou concluída")
    })
    public ResponseEntity<AppointmentResponse> reschedule(
        @PathVariable UUID id,
        @Valid @RequestBody RescheduleAppointmentRequest request
    ) {
        Appointment appointment = service.reschedule(
            id,
            request.scheduledAt(),
            request.fitIn(),
            request.fitInReason()
        );
        return ResponseEntity.ok(new AppointmentResponse(appointment.getId()));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Cancela uma consulta")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Consulta cancelada"),
        @ApiResponse(responseCode = "401", description = "Sem token ou token inválido"),
        @ApiResponse(responseCode = "403", description = "Papel não autorizado"),
        @ApiResponse(responseCode = "404", description = "Consulta inexistente"),
        @ApiResponse(responseCode = "409", description = "Consulta já iniciada, cancelada ou concluída")
    })
    public ResponseEntity<Void> cancel(@PathVariable UUID id) {
        service.cancel(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(
        summary = "Marca uma consulta como realizada",
        description = "Idempotente: repetir devolve 204 sem publicar segundo evento."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Consulta concluída, ou já estava"),
        @ApiResponse(responseCode = "401", description = "Sem token ou token inválido"),
        @ApiResponse(responseCode = "403", description = "Papel não autorizado"),
        @ApiResponse(responseCode = "404", description = "Consulta inexistente"),
        @ApiResponse(responseCode = "409", description = "Consulta cancelada")
    })
    public ResponseEntity<Void> complete(@PathVariable UUID id) {
        service.complete(id);
        return ResponseEntity.noContent().build();
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
