package com.fiap.hospital.archrule.fixture.accounts;

import com.fiap.hospital.archrule.fixture.activation.repository.FixtureActivationRepository;

public class FixtureAccountService {

    private final FixtureActivationRepository forbidden = new FixtureActivationRepository();

    public FixtureActivationRepository forbidden() {
        return forbidden;
    }
}
