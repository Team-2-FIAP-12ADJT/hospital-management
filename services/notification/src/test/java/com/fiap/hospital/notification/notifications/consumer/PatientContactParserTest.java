package com.fiap.hospital.notification.notifications.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fiap.hospital.notification.notifications.service.PatientContact;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PatientContactParserTest {

    private final PatientContactParser parser = new PatientContactParser();

    @Test
    void readsTheDeliveryAddressFromTheRegistration() {
        UUID eventId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();

        PatientContact contact = parser.parse(
            EventFixtures.patientRegistered(eventId, patientId, "marcos@exemplo.com")
        );

        assertThat(contact.eventId()).isEqualTo(eventId);
        assertThat(contact.patientId()).isEqualTo(patientId);
        assertThat(contact.email()).isEqualTo("marcos@exemplo.com");
        assertThat(contact.phone()).isEqualTo("+5511998877665");
        assertThat(contact.occurredAt()).isEqualTo(EventFixtures.OCCURRED_AT);
    }

    @Test
    void readsTheCorrectedAddressFromTheContactUpdate() {
        Instant later = EventFixtures.OCCURRED_AT.plusSeconds(60);

        PatientContact contact = parser.parse(EventFixtures.patientContactUpdated(
            UUID.randomUUID(), UUID.randomUUID(), "novo@exemplo.com", later
        ));

        assertThat(contact.email()).isEqualTo("novo@exemplo.com");
        assertThat(contact.occurredAt()).isEqualTo(later);
    }

    @Test
    void phoneIsOptional() {
        PatientContact contact = parser.parse(EventFixtures.patientContactUpdated(
            UUID.randomUUID(), UUID.randomUUID(), "novo@exemplo.com", EventFixtures.OCCURRED_AT
        ));

        assertThat(contact.phone()).isNull();
    }

    @Test
    void refusesDoctorRegisteredBecauseNoReminderIsSentToADoctor() {
        assertThatThrownBy(() -> parser.parse(EventFixtures.doctorRegistered(UUID.randomUUID())))
            .isInstanceOf(UnsupportedEventException.class)
            .hasMessage("DoctorRegistered");
    }

    @Test
    void refusesRegistrationWithoutEmail() {
        String withoutEmail = EventFixtures
            .patientRegistered(UUID.randomUUID(), UUID.randomUUID(), "marcos@exemplo.com")
            .replace("\"email\": \"marcos@exemplo.com\"", "\"email\": null");

        assertThatThrownBy(() -> parser.parse(withoutEmail))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("email");
    }
}
