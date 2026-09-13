package com.fiap.hospital.notification.notifications.service;

import com.fiap.hospital.notification.notifications.domain.Notification;
import com.fiap.hospital.notification.notifications.domain.NotificationStatus;
import com.fiap.hospital.notification.notifications.repository.NotificationRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationFailureRecorder {

    private final NotificationRepository notifications;
    private final NotificationProperties properties;

    public NotificationFailureRecorder(
        NotificationRepository notifications,
        NotificationProperties properties
    ) {
        this.notifications = notifications;
        this.properties = properties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordUnexpectedFailure(UUID notificationId) {
        Notification notification = notifications.findById(notificationId).orElse(null);
        if (notification == null || notification.getStatus() != NotificationStatus.PENDING) {
            return;
        }

        notification.recordFailedAttempt();
        if (notification.getAttempts() >= properties.maxAttempts()) {
            notification.markAbandoned();
        }
        notifications.save(notification);
    }
}
