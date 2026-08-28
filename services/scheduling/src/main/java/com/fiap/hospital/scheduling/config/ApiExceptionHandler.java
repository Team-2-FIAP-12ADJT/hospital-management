package com.fiap.hospital.scheduling.config;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Sem @ControllerAdvice o scheduling devolveria 500 quando a violação de UNIQUE
// (tax_identifier ou crm) escapa da checagem prévia de existência sob concorrência
// (ADR-0010 / @ApiResponse(409) do DoctorController). Traduzir para CONFLICT deixa
// o contrato honesto: a resposta reflete a regra de negócio, não um erro interno.
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Void> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }
}
