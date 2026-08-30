package com.fiap.hospital.archrule.fixture.appointments;

import com.fiap.hospital.archrule.fixture.participants.api.FixtureParticipantRequest;

/**
 * A violação que appointmentsDoNotReachParticipantInternals espera encontrar.
 * O import da API de escrita está errado de propósito.
 */
public class FixtureAppointmentApiClient {

    private final FixtureParticipantRequest forbidden = new FixtureParticipantRequest();

    public FixtureParticipantRequest forbidden() {
        return forbidden;
    }
}
