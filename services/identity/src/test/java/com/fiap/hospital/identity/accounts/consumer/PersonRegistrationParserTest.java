package com.fiap.hospital.identity.accounts.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fiap.hospital.identity.accounts.domain.Role;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PersonRegistrationParserTest {

    private final PersonRegistrationParser parser = new PersonRegistrationParser();

    @Test
    void lePatientRegisteredPeloContrato() {
        UUID eventId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();

        PersonRegistration registration = parser.parse(
            PersonEventFixtures.patientRegistered(eventId, patientId, "52998224726")
        );

        assertThat(registration.eventId()).isEqualTo(eventId);
        assertThat(registration.eventType()).isEqualTo("PatientRegistered");
        assertThat(registration.personId()).isEqualTo(patientId);
        assertThat(registration.taxIdentifier()).isEqualTo("52998224726");
        assertThat(registration.name()).isEqualTo("Ana Ribeiro");
        assertThat(registration.email()).isEqualTo("ana.ribeiro@exemplo.com");
        assertThat(registration.role()).isEqualTo(Role.PATIENT);
    }

    @Test
    void leDoctorRegisteredEIgnoraCrmEEspecialidade() {
        UUID eventId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();

        PersonRegistration registration = parser.parse(
            PersonEventFixtures.doctorRegistered(eventId, doctorId, "39053344706")
        );

        assertThat(registration.personId()).isEqualTo(doctorId);
        assertThat(registration.taxIdentifier()).isEqualTo("39053344706");
        assertThat(registration.role()).isEqualTo(Role.DOCTOR);
        assertThat(registration.name()).isEqualTo("Dr. Paulo Menezes");
        assertThat(registration.email()).isEqualTo("paulo.menezes@hospital.local");
    }

    @Test
    void recusaTipoForaDoProvisionamento() {
        assertThatThrownBy(() -> parser.parse(
            PersonEventFixtures.contactUpdated(UUID.randomUUID(), UUID.randomUUID())
        )).isInstanceOf(UnsupportedPersonEventException.class);
    }

    @Test
    void recusaEnvelopeSemData() {
        String json = """
            {"eventId":"%s","eventType":"PatientRegistered","eventVersion":1,"occurredAt":"2026-08-17T14:05:03.123Z"}
            """.formatted(UUID.randomUUID());

        assertThatThrownBy(() -> parser.parse(json))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("data");
    }
}
