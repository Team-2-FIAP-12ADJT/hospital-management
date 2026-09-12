package com.fiap.hospital.notification.notifications.service;

import com.fiap.hospital.notification.notifications.domain.Notification;
import com.fiap.hospital.notification.notifications.repository.NotificationRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationDispatcher {

    private final NotificationRepository notifications;
    private final NotificationDelivery delivery;
    private final NotificationProperties properties;
    private final Clock clock;

    public NotificationDispatcher(
        NotificationRepository notifications,
        NotificationDelivery delivery,
        NotificationProperties properties,
        Clock clock
    ) {
        this.notifications = notifications;
        this.delivery = delivery;
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
            properties.maxAttempts(),
            Limit.of(properties.dispatchBatchSize())
        );
        due.forEach(notification -> delivery.deliver(notification.getId()));
    }
}
