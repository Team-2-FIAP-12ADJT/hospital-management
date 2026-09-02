package com.fiap.hospital.archrule.fixture.participants;

import com.fiap.hospital.archrule.fixture.appointments.domain.FixtureAppointmentDomain;

public class FixtureParticipantUsingAppointmentDomain {

    private final FixtureAppointmentDomain forbidden = new FixtureAppointmentDomain();

    public FixtureAppointmentDomain forbidden() {
        return forbidden;
    }
}
