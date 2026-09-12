package com.fiap.hospital.notification.invite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
class ActivationInviteMailer {

    private final JavaMailSender mailSender;
    private final String from;
    private final String activateUrl;

    ActivationInviteMailer(
        JavaMailSender mailSender,
        @Value("${MAIL_FROM:noreply@hospital.local}") String from,
        @Value("${GATEWAY_PUBLIC_URL:http://localhost:8080}") String gatewayPublicUrl
    ) {
        this.mailSender = mailSender;
        this.from = from;
        this.activateUrl = gatewayPublicUrl.replaceAll("/$", "") + "/auth/activate";
    }

    void send(ActivationInvite invite) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(invite.email());
        message.setSubject("Ative sua conta no hospital");
        message.setText(
            "Olá, "
                + invite.name()
                + ".\n\n"
                + "Defina sua senha com POST "
                + activateUrl
                + " enviando o token e a senha no corpo.\n\n"
                + "activationToken="
                + invite.activationToken()
                + "\n"
                + "expiresAt="
                + invite.expiresAt()
                + "\n"
        );
        mailSender.send(message);
    }
}
