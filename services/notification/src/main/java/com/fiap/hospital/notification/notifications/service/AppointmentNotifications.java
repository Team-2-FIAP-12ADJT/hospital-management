package com.fiap.hospital.notification.notifications.service;

import com.fiap.hospital.notification.idempotency.IdempotencyService;
import com.fiap.hospital.notification.notifications.domain.Notification;
import com.fiap.hospital.notification.notifications.repository.NotificationRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AppointmentNotifications {

    public static final String CONSUMER = "appointment";

    private static final Logger log = LoggerFactory.getLogger(AppointmentNotifications.class);

    private final IdempotencyService idempotencyService;
    private final NotificationRepository notifications;
    private final NotificationProperties properties;
    private final Clock clock;

    public AppointmentNotifications(
        IdempotencyService idempotencyService,
        NotificationRepository notifications,
        NotificationProperties properties,
        Clock clock
    ) {
        this.idempotencyService = idempotencyService;
        this.notifications = notifications;
        this.properties = properties;
        this.clock = clock;
    }

    public void schedule(ScheduledAppointment appointment) {
        idempotencyService.process(CONSUMER, appointment.eventId(), () -> {
            Instant now = clock.instant();
            confirm(appointment.patientId(), appointment.appointmentId(),
                appointment.scheduledAt(), appointment.doctorName(),
                appointment.doctorSpecialty(), now);
            remind(appointment.patientId(), appointment.appointmentId(),
                appointment.scheduledAt(), appointment.doctorName(),
                appointment.doctorSpecialty(), now);
        });
    }

    public void reschedule(RescheduledAppointment appointment) {
        idempotencyService.process(CONSUMER, appointment.eventId(), () -> {
            Instant now = clock.instant();
            // O cancelamento do antigo tem de chegar ao banco antes da inserção do
            // novo: o Hibernate ordena insert antes de update, e o índice único do
            // lembrete pendente recusaria os dois convivendo.
            cancelPendingReminder(appointment.appointmentId());
            notifications.flush();

            confirm(appointment.patientId(), appointment.appointmentId(),
                appointment.scheduledAt(), appointment.doctorName(),
                appointment.doctorSpecialty(), now);
            remind(appointment.patientId(), appointment.appointmentId(),
                appointment.scheduledAt(), appointment.doctorName(),
                appointment.doctorSpecialty(), now);
        });
    }

    public void cancel(CancelledAppointment appointment) {
        idempotencyService.process(CONSUMER, appointment.eventId(), () ->
            cancelPendingReminder(appointment.appointmentId())
        );
    }

    /**
     * Lembrete já enviado não aparece na busca, então cancelar consulta depois do
     * disparo não é erro — não há o que cancelar.
     */
    private void cancelPendingReminder(UUID appointmentId) {
        notifications.findPendingReminder(appointmentId).ifPresent(reminder -> {
            reminder.cancel();
            notifications.save(reminder);
        });
    }

    private void confirm(
        UUID patientId, UUID appointmentId, Instant scheduledAt,
        String doctorName, String doctorSpecialty, Instant now
    ) {
        notifications.save(Notification.confirmation(
            UUID.randomUUID(), patientId, appointmentId,
            scheduledAt, doctorName, doctorSpecialty, now
        ));
    }

    private void remind(
        UUID patientId, UUID appointmentId, Instant scheduledAt,
        String doctorName, String doctorSpecialty, Instant now
    ) {
        Instant fireAt = scheduledAt.minus(properties.reminderLeadTime());
        Notification.reminder(
            UUID.randomUUID(), patientId, appointmentId,
            scheduledAt, doctorName, doctorSpecialty, fireAt, now
        ).ifPresentOrElse(
            notifications::save,
            () -> log.info(
                "no reminder created, lead time already elapsed appointmentId={} scheduledAt={}",
                appointmentId, scheduledAt
            )
        );
    }
}
