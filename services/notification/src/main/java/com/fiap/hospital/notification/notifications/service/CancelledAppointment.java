package com.fiap.hospital.notification.notifications.service;

import java.util.UUID;

public record CancelledAppointment(
    UUID eventId,
    UUID appointmentId,
    UUID patientId
) implements AppointmentEvent {
}
