package com.fiap.hospital.notification.notifications.consumer;

import com.fiap.hospital.notification.notifications.service.PatientContact;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class PatientContactParser {

    static final String PATIENT_REGISTERED = "PatientRegistered";
    static final String PATIENT_CONTACT_UPDATED = "PatientContactUpdated";

    private static final Set<String> SUPPORTED_TYPES =
        Set.of(PATIENT_REGISTERED, PATIENT_CONTACT_UPDATED);

    private final JsonMapper mapper = JsonMapper.builder().build();

    PatientContact parse(String envelopeJson) {
        EventEnvelope envelope = EventEnvelope.from(mapper.readTree(envelopeJson));
        if (!SUPPORTED_TYPES.contains(envelope.eventType())) {
            throw new UnsupportedEventException(envelope.eventType());
        }
        return new PatientContact(
            envelope.eventId(),
            envelope.requiredUuid("patientId"),
            envelope.requiredText("email"),
            envelope.optionalText("phone"),
            envelope.occurredAt()
        );
    }
}
