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
public class ScheduleAppointmentNotifications {

    public static final String CONSUMER = "appointment";

    private static final Logger log =
        LoggerFactory.getLogger(ScheduleAppointmentNotifications.class);

    private final IdempotencyService idempotencyService;
    private final NotificationRepository notifications;
    private final NotificationProperties properties;
    private final Clock clock;

    public ScheduleAppointmentNotifications(
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
            notifications.save(Notification.confirmation(
                UUID.randomUUID(),
                appointment.patientId(),
                appointment.appointmentId(),
                appointment.scheduledAt(),
                appointment.doctorName(),
                appointment.doctorSpecialty(),
                now
            ));

            Instant fireAt = appointment.scheduledAt().minus(properties.reminderLeadTime());
            Notification.reminder(
                UUID.randomUUID(),
                appointment.patientId(),
                appointment.appointmentId(),
                appointment.scheduledAt(),
                appointment.doctorName(),
                appointment.doctorSpecialty(),
                fireAt,
                now
            ).ifPresentOrElse(
                notifications::save,
                () -> log.info(
                    "no reminder created, lead time already elapsed appointmentId={} scheduledAt={}",
                    appointment.appointmentId(), appointment.scheduledAt()
                )
            );
        });
    }
}
