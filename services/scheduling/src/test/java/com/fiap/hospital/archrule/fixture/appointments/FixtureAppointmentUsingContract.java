package com.fiap.hospital.archrule.fixture.appointments;

import com.fiap.hospital.archrule.fixture.participants.FixtureParticipantContract;

/**
 * A direção permitida: appointments referenciando o contrato publicado por
 * participants (o equivalente a @ManyToOne Patient), não o interno.
 */
public class FixtureAppointmentUsingContract {

    private final FixtureParticipantContract allowed = new FixtureParticipantContract();

    public FixtureParticipantContract allowed() {
        return allowed;
    }
}
