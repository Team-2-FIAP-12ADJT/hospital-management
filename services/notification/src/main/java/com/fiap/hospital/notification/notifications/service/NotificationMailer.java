package com.fiap.hospital.notification.notifications.service;

import com.fiap.hospital.notification.notifications.domain.Notification;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Component;

@Component
public class NotificationMailer {

    private static final DateTimeFormatter WHEN =
        DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", Locale.of("pt", "BR"));

    private final MailSender mailSender;
    private final NotificationProperties properties;

    public NotificationMailer(MailSender mailSender, NotificationProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    public void send(Notification notification, String recipient) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.fromAddress());
        message.setTo(recipient);
        message.setSubject(notification.getKind().subject());
        message.setText(body(notification));
        mailSender.send(message);
    }

    private String body(Notification notification) {
        String when = WHEN.format(notification.getScheduledAt().atZone(properties.displayZone()));
        return """
            %s

            Data: %s
            Profissional: %s (%s)

            Em caso de imprevisto, entre em contato com a recepção.
            """.formatted(
                notification.getKind().opening(),
                when,
                notification.getDoctorName(),
                notification.getDoctorSpecialty()
            );
    }
}
