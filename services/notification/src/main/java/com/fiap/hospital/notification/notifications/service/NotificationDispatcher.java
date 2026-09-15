package com.fiap.hospital.notification.notifications.service;

import com.fiap.hospital.notification.notifications.domain.Notification;
import com.fiap.hospital.notification.notifications.repository.NotificationRepository;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final NotificationRepository notifications;
    private final NotificationDelivery delivery;
    private final NotificationFailureRecorder failureRecorder;
    private final NotificationProperties properties;
    private final Clock clock;

    public NotificationDispatcher(
        NotificationRepository notifications,
        NotificationDelivery delivery,
        NotificationFailureRecorder failureRecorder,
        NotificationProperties properties,
        Clock clock
    ) {
        this.notifications = notifications;
        this.delivery = delivery;
        this.failureRecorder = failureRecorder;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Uma transação por notificação: falha de uma não desfaz a entrega das
     * outras do mesmo lote.
     */
    @Scheduled(fixedDelayString = "${notification.sweep-interval}")
    public void sweep() {
        List<Notification> due = notifications.findDue(
            clock.instant(),
            Limit.of(properties.dispatchBatchSize())
        );
        due.forEach(notification -> {
            try {
                delivery.deliver(notification.getId());
            } catch (RuntimeException exception) {
                log.error(
                    "notification delivery failed id={}, continuing sweep",
                    notification.getId(),
                    exception
                );
                try {
                    failureRecorder.recordUnexpectedFailure(notification.getId());
                } catch (RuntimeException recorderException) {
                    log.error(
                        "failed to record notification delivery failure id={}",
                        notification.getId(),
                        recorderException
                    );
                }
            }
        });
    }
}
