package com.fiap.hospital.identity.accounts.consumer;

import com.fiap.hospital.identity.accounts.domain.Role;
import java.util.UUID;

record PersonRegistration(
    UUID eventId,
    String eventType,
    UUID personId,
    String taxIdentifier,
    String name,
    String email,
    Role role
) {}
