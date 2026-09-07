package com.fiap.hospital.notification.notifications.consumer;

import java.time.Instant;
import java.util.UUID;

final class EventFixtures {

    static final Instant OCCURRED_AT = Instant.parse("2026-09-07T12:00:00.000Z");
    static final Instant SCHEDULED_AT = Instant.parse("2026-09-10T13:30:00.000Z");

    private EventFixtures() {
    }

    static String appointmentScheduled(UUID eventId, UUID appointmentId, UUID patientId) {
        return """
            {
              "eventId": "%s",
              "eventType": "AppointmentScheduled",
              "eventVersion": 1,
              "occurredAt": "%s",
              "data": {
                "appointmentId": "%s",
                "patientId": "%s",
                "doctorId": "%s",
                "scheduledAt": "%s",
                "status": "SCHEDULED",
                "fitIn": false,
                "fitInReason": null,
                "patientName": "Marcos Vieira",
                "doctorName": "Dra. Helena Prado",
                "doctorSpecialty": "Cardiologia"
              }
            }
            """.formatted(
                eventId, OCCURRED_AT, appointmentId, patientId, UUID.randomUUID(), SCHEDULED_AT
            );
    }

    static String patientRegistered(UUID eventId, UUID patientId, String email) {
        return """
            {
              "eventId": "%s",
              "eventType": "PatientRegistered",
              "eventVersion": 1,
              "occurredAt": "%s",
              "data": {
                "patientId": "%s",
                "taxIdentifier": "52998224725",
                "name": "Marcos Vieira",
                "email": "%s",
                "phone": "+5511998877665",
                "role": "PATIENT"
              }
            }
            """.formatted(eventId, OCCURRED_AT, patientId, email);
    }

    static String patientContactUpdated(UUID eventId, UUID patientId, String email, Instant occurredAt) {
        return """
            {
              "eventId": "%s",
              "eventType": "PatientContactUpdated",
              "eventVersion": 1,
              "occurredAt": "%s",
              "data": {
                "patientId": "%s",
                "email": "%s"
              }
            }
            """.formatted(eventId, occurredAt, patientId, email);
    }

    static String appointmentRescheduled(
        UUID eventId, UUID appointmentId, UUID patientId, Instant previous, Instant scheduledAt
    ) {
        return """
            {
              "eventId": "%s",
              "eventType": "AppointmentRescheduled",
              "eventVersion": 1,
              "occurredAt": "%s",
              "data": {
                "appointmentId": "%s",
                "patientId": "%s",
                "doctorId": "%s",
                "previousScheduledAt": "%s",
                "scheduledAt": "%s",
                "status": "SCHEDULED",
                "fitIn": false,
                "fitInReason": null,
                "patientName": "Marcos Vieira",
                "doctorName": "Dra. Helena Prado",
                "doctorSpecialty": "Cardiologia"
              }
            }
            """.formatted(
                eventId, OCCURRED_AT, appointmentId, patientId, UUID.randomUUID(),
                previous, scheduledAt
            );
    }

    static String appointmentCancelled(UUID eventId, UUID appointmentId, UUID patientId) {
        return """
            {
              "eventId": "%s",
              "eventType": "AppointmentCancelled",
              "eventVersion": 1,
              "occurredAt": "%s",
              "data": {
                "appointmentId": "%s",
                "patientId": "%s",
                "doctorId": "%s",
                "scheduledAt": "%s",
                "status": "CANCELLED",
                "cancelledAt": "%s"
              }
            }
            """.formatted(
                eventId, OCCURRED_AT, appointmentId, patientId, UUID.randomUUID(),
                SCHEDULED_AT, OCCURRED_AT
            );
    }

    static String appointmentCompleted(UUID eventId, UUID appointmentId, UUID patientId) {
        return """
            {
              "eventId": "%s",
              "eventType": "AppointmentCompleted",
              "eventVersion": 1,
              "occurredAt": "%s",
              "data": {
                "appointmentId": "%s",
                "patientId": "%s",
                "doctorId": "%s",
                "scheduledAt": "%s",
                "status": "COMPLETED",
                "completedAt": "%s"
              }
            }
            """.formatted(
                eventId, OCCURRED_AT, appointmentId, patientId, UUID.randomUUID(),
                SCHEDULED_AT, OCCURRED_AT
            );
    }

    static String doctorRegistered(UUID eventId) {
        return """
            {
              "eventId": "%s",
              "eventType": "DoctorRegistered",
              "eventVersion": 1,
              "occurredAt": "%s",
              "data": {
                "doctorId": "%s",
                "taxIdentifier": "39053344705",
                "name": "Dra. Helena Prado",
                "email": "helena@exemplo.com",
                "role": "DOCTOR"
              }
            }
            """.formatted(eventId, OCCURRED_AT, UUID.randomUUID());
    }
}
