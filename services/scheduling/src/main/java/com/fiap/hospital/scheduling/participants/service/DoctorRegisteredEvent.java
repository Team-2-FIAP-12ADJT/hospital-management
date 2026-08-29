package com.fiap.hospital.scheduling.participants.service;

import java.util.UUID;

// Payload de `DoctorRegistered` (docs/contracts/pessoa-e-conta.md). O `role` vai
// sempre "DOCTOR": quem decide o papel é este serviço, nunca o corpo da requisição.
public record DoctorRegisteredEvent(
    UUID doctorId,
    String taxIdentifier,
    String crm,
    String specialty,
    String name,
    String email,
    String role
) {}
