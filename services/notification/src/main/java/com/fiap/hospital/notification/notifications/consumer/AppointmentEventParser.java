package com.fiap.hospital.notification.notifications.consumer;

import com.fiap.hospital.notification.notifications.service.AppointmentEvent;
import com.fiap.hospital.notification.notifications.service.CancelledAppointment;
import com.fiap.hospital.notification.notifications.service.RescheduledAppointment;
import com.fiap.hospital.notification.notifications.service.ScheduledAppointment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class AppointmentEventParser {

    static final String APPOINTMENT_SCHEDULED = "AppointmentScheduled";
    static final String APPOINTMENT_RESCHEDULED = "AppointmentRescheduled";
    static final String APPOINTMENT_CANCELLED = "AppointmentCancelled";

    private final JsonMapper mapper = JsonMapper.builder().build();

    AppointmentEvent parse(String envelopeJson) {
        EventEnvelope envelope = EventEnvelope.from(mapper.readTree(envelopeJson));
        return switch (envelope.eventType()) {
            case APPOINTMENT_SCHEDULED -> new ScheduledAppointment(
                envelope.eventId(),
                envelope.requiredUuid("appointmentId"),
                envelope.requiredUuid("patientId"),
                envelope.requiredInstant("scheduledAt"),
                envelope.requiredText("doctorName"),
                envelope.requiredText("doctorSpecialty")
            );
            case APPOINTMENT_RESCHEDULED -> new RescheduledAppointment(
                envelope.eventId(),
                envelope.requiredUuid("appointmentId"),
                envelope.requiredUuid("patientId"),
                envelope.requiredInstant("previousScheduledAt"),
                envelope.requiredInstant("scheduledAt"),
                envelope.requiredText("doctorName"),
                envelope.requiredText("doctorSpecialty")
            );
            case APPOINTMENT_CANCELLED -> new CancelledAppointment(
                envelope.eventId(),
                envelope.requiredUuid("appointmentId"),
                envelope.requiredUuid("patientId")
            );
            default -> throw new UnsupportedEventException(envelope.eventType());
        };
    }
}
