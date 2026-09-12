package com.fiap.hospital.notification.notifications.consumer;

import com.fiap.hospital.notification.notifications.service.ScheduledAppointment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class AppointmentEventParser {

    static final String APPOINTMENT_SCHEDULED = "AppointmentScheduled";

    private final JsonMapper mapper = JsonMapper.builder().build();

    ScheduledAppointment parse(String envelopeJson) {
        EventEnvelope envelope = EventEnvelope.from(mapper.readTree(envelopeJson));
        if (!APPOINTMENT_SCHEDULED.equals(envelope.eventType())) {
            throw new UnsupportedEventException(envelope.eventType());
        }
        return new ScheduledAppointment(
            envelope.eventId(),
            envelope.requiredUuid("appointmentId"),
            envelope.requiredUuid("patientId"),
            envelope.requiredInstant("scheduledAt"),
            envelope.requiredText("doctorName"),
            envelope.requiredText("doctorSpecialty")
        );
    }
}
