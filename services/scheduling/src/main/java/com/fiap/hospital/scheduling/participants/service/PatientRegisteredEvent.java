package com.fiap.hospital.scheduling.participants.service;

import java.util.UUID;

// Payload de `PatientRegistered` (docs/contracts/pessoa-e-conta.md). O `role` vai
// sempre "PATIENT": quem decide o papel é este serviço, nunca o corpo da requisição.
public record PatientRegisteredEvent(
    UUID patientId,
    String taxIdentifier,
    String name,
    String email,
    String phone,
    String role
) {}
