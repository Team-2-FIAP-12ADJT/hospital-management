package com.fiap.hospital.notification.invite;

import java.time.Instant;
import java.util.UUID;

record ActivationInvite(
    UUID eventId,
    UUID userId,
    String name,
    String email,
    String role,
    String activationToken,
    Instant expiresAt
) {}
