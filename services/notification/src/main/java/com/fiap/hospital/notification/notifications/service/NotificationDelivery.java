package com.fiap.hospital.notification.notifications.service;

import com.fiap.hospital.notification.notifications.domain.ContactReplica;
import com.fiap.hospital.notification.notifications.domain.Notification;
import com.fiap.hospital.notification.notifications.domain.NotificationStatus;
import com.fiap.hospital.notification.notifications.repository.ContactReplicaRepository;
import com.fiap.hospital.notification.notifications.repository.NotificationRepository;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationDelivery {

    private static final Logger log = LoggerFactory.getLogger(NotificationDelivery.class);

    private final NotificationRepository notifications;
    private final ContactReplicaRepository contacts;
    private final NotificationMailer mailer;
    private final Clock clock;

    public NotificationDelivery(
        NotificationRepository notifications,
        ContactReplicaRepository contacts,
        NotificationMailer mailer,
        Clock clock
    ) {
        this.notifications = notifications;
        this.contacts = contacts;
        this.mailer = mailer;
        this.clock = clock;
    }

    @Transactional
    public void deliver(UUID notificationId) {
        Notification notification = notifications.findById(notificationId).orElse(null);
        if (notification == null || notification.getStatus() != NotificationStatus.PENDING) {
            return;
        }

        ContactReplica contact = contacts.findById(notification.getPatientId()).orElse(null);
        if (contact == null) {
            notification.recordFailedAttempt();
            notifications.save(notification);
            log.warn(
                "contact replica not available yet, notification stays pending id={} patientId={} attempts={}",
                notification.getId(), notification.getPatientId(), notification.getAttempts()
            );
            return;
        }

        try {
            mailer.send(notification, contact.getEmail());
        } catch (RuntimeException exception) {
            notification.recordFailedAttempt();
            notifications.save(notification);
            log.error(
                "failed to send notification id={} kind={} attempts={}: {}",
                notification.getId(), notification.getKind(), notification.getAttempts(),
                exception.getMessage()
            );
            return;
        }

        notification.markSent(clock.instant());
        notifications.save(notification);
    }
}
