package com.fiap.hospital.identity.accounts.consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PersonEventConsumerTest {

    @Mock
    private ProvisionAccountFromPersonEvent provisionAccount;

    @Test
    void discardsMalformedJson() {
        receive("{not-json");

        verifyNoInteractions(provisionAccount);
    }

    @Test
    void discardsEnvelopeMissingRequiredField() {
        receive("{\"eventId\":\"" + UUID.randomUUID() + "\"}");

        verifyNoInteractions(provisionAccount);
    }

    @Test
    void ignoresUnsupportedEventType() {
        receive(PersonEventFixtures.contactUpdated(UUID.randomUUID(), UUID.randomUUID()));

        verifyNoInteractions(provisionAccount);
    }

    @Test
    void provisionsPatientRegisteredFromKafkaRecord() {
        UUID eventId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();

        receive(PersonEventFixtures.patientRegistered(eventId, patientId, "52998224726"));

        verify(provisionAccount).provision(any(PersonRegistration.class));
    }

    private void receive(String value) {
        new PersonEventConsumer(new PersonRegistrationParser(), provisionAccount)
            .receive(new ConsumerRecord<>("hospital.person", 0, 0L, null, value));
    }
}
