package com.fiap.hospital.notification.notifications.service;

import java.util.UUID;

/**
 * As três espécies que o notification consome. AppointmentCompleted fica de fora
 * de propósito: o lembrete já saiu antes da consulta acontecer.
 */
public sealed interface AppointmentEvent
    permits ScheduledAppointment, RescheduledAppointment, CancelledAppointment {

    UUID eventId();

    UUID appointmentId();
}
