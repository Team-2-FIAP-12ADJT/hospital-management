package com.fiap.hospital.archrule.fixture.appointments;

import com.fiap.hospital.archrule.fixture.participants.service.FixtureParticipantService;

/**
 * Simula appointments disparando uma escrita de participante.
 */
public class FixtureAppointmentWriterClient {

    private final FixtureParticipantService forbidden = new FixtureParticipantService();

    public FixtureParticipantService forbidden() {
        return forbidden;
    }
}
