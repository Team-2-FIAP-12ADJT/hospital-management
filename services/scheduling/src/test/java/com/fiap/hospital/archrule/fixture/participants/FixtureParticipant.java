package com.fiap.hospital.archrule.fixture.participants;

import com.fiap.hospital.archrule.fixture.appointments.FixtureAppointment;

/**
 * A violação que DependencyRuleBitesTest espera encontrar. O import está errado
 * de propósito — removê-lo deixa a regra sem nada para reprovar.
 *
 * Fora de com.fiap.hospital.scheduling para não entrar na varredura da regra real.
 */
public class FixtureParticipant {

    private final FixtureAppointment forbidden = new FixtureAppointment();

    public FixtureAppointment forbidden() {
        return forbidden;
    }
}
