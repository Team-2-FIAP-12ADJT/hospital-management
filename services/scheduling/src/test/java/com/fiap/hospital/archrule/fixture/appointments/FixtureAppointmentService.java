package com.fiap.hospital.archrule.fixture.appointments;

import com.fiap.hospital.archrule.fixture.participants.repository.FixtureParticipantRepository;

/**
 * A violação que appointmentsDoNotReachParticipantInternals espera encontrar.
 * O import está errado de propósito.
 */
public class FixtureAppointmentService {

    private final FixtureParticipantRepository forbidden = new FixtureParticipantRepository();

    public FixtureParticipantRepository forbidden() {
        return forbidden;
    }
}
