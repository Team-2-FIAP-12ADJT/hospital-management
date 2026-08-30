package com.fiap.hospital.archrule.fixture.appointments;

import com.fiap.hospital.archrule.fixture.participants.internal.FixtureParticipantInternal;

public class FixtureAppointmentInternalClient {

    private final FixtureParticipantInternal participant = new FixtureParticipantInternal();

    public FixtureParticipantInternal participant() {
        return participant;
    }
}
