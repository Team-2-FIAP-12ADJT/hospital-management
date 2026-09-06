package com.fiap.hospital.scheduling.participants.contract;

import java.util.UUID;

public record DoctorSummary(UUID id, String name, String specialty) {}
