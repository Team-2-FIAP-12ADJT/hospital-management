package com.fiap.hospital.archrule.fixture.outbox;

import com.fiap.hospital.archrule.fixture.availability.FixtureAvailabilityContract;

public class FixtureFeatureAwareOutbox {

    private FixtureAvailabilityContract forbidden;

    public FixtureAvailabilityContract forbidden() {
        return forbidden;
    }
}
