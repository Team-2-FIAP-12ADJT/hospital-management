package com.fiap.hospital.archrule.fixture.appointments;

import com.fiap.hospital.scheduling.participants.domain.Doctor;
import com.fiap.hospital.scheduling.participants.domain.Patient;
import jakarta.persistence.ManyToOne;

public class FixtureAppointmentUsingPublishedAggregates {

    @ManyToOne
    private Patient patient;

    @ManyToOne
    private Doctor doctor;

    public Patient patient() {
        return patient;
    }

    public Doctor doctor() {
        return doctor;
    }
}
