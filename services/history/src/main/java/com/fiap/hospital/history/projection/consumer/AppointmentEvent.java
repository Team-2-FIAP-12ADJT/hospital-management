package com.fiap.hospital.history.projection.consumer;

import java.time.Instant;
import java.util.UUID;

sealed interface AppointmentEvent
        permits AppointmentScheduledMessage, AppointmentRescheduledMessage,
        AppointmentCancelledMessage, AppointmentCompletedMessage {

    UUID eventId();

    Instant occurredAt();

    UUID appointmentId();
}
