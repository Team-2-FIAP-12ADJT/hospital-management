package com.fiap.hospital.notification.notifications.service;

import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification")
public record NotificationProperties(
    Duration reminderLeadTime,
    short maxAttempts,
    int dispatchBatchSize,
    String fromAddress,
    ZoneId displayZone
) {
}
