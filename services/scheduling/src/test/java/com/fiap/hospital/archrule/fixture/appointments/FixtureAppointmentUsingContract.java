package com.fiap.hospital.archrule.fixture.appointments;

import com.fiap.hospital.archrule.fixture.participants.contract.FixtureParticipantContract;

/**
 * A direção permitida: appointments referenciando o contrato publicado por
 * participants (o equivalente a @ManyToOne Patient), não o interno.
 */
public class FixtureAppointmentUsingContract {

    private FixtureParticipantContract allowed;

    public FixtureParticipantContract allowed() {
        return allowed;
    }
}
