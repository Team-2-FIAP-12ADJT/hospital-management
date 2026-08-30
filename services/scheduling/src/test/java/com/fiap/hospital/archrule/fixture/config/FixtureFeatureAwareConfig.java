package com.fiap.hospital.archrule.fixture.config;

import com.fiap.hospital.archrule.fixture.participants.contract.FixtureParticipantContract;

/**
 * A violação que sharedPackagesKnowNoFeature espera encontrar.
 * O pacote compartilhado conhece uma feature de propósito.
 */
public class FixtureFeatureAwareConfig {

    private FixtureParticipantContract forbidden;

    public FixtureParticipantContract forbidden() {
        return forbidden;
    }
}
