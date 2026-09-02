package com.fiap.hospital.archrule.fixture.appointments;

import com.fiap.hospital.archrule.fixture.availability.internal.FixtureAvailabilityInternal;

public class FixtureAppointmentUsingAvailabilityInternal {

    private final FixtureAvailabilityInternal dependency = new FixtureAvailabilityInternal();

    public FixtureAvailabilityInternal dependency() {
        return dependency;
    }
}
