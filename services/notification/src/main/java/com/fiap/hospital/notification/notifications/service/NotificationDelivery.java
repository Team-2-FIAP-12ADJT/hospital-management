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
    private final NotificationProperties properties;
    private final Clock clock;

    public NotificationDelivery(
        NotificationRepository notifications,
        ContactReplicaRepository contacts,
        NotificationMailer mailer,
        NotificationProperties properties,
        Clock clock
    ) {
        this.notifications = notifications;
        this.contacts = contacts;
        this.mailer = mailer;
        this.properties = properties;
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
            retryOrGiveUp(notification, "contact replica not available yet");
            return;
        }

        try {
            mailer.send(notification, contact.getEmail());
        } catch (RuntimeException exception) {
            retryOrGiveUp(notification, "send failed: " + exception.getMessage());
            return;
        }

        notification.markSent(clock.instant());
        notifications.save(notification);
    }

    /**
     * Esgotar o teto é registrado em ERROR porque a linha para de ser varrida
     * sem mudar de estado: sem esta linha, uma notificação abandonada fica
     * indistinguível de uma ainda na fila.
     */
    private void retryOrGiveUp(Notification notification, String cause) {
        notification.recordFailedAttempt();
        notifications.save(notification);

        if (notification.getAttempts() >= properties.maxAttempts()) {
            log.error(
                "giving up on notification id={} kind={} patientId={} after {} attempts: {}",
                notification.getId(), notification.getKind(), notification.getPatientId(),
                notification.getAttempts(), cause
            );
            return;
        }

        log.warn(
            "notification stays pending id={} kind={} patientId={} attempts={}: {}",
            notification.getId(), notification.getKind(), notification.getPatientId(),
            notification.getAttempts(), cause
        );
    }
}
