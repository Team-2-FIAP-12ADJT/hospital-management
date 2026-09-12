package com.fiap.hospital.notification.notifications.service;

import java.time.Instant;
import java.util.UUID;

public record PatientContact(
    UUID eventId,
    UUID patientId,
    String email,
    String phone,
    Instant occurredAt
) {
}
