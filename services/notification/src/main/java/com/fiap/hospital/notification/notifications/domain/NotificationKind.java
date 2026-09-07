package com.fiap.hospital.notification.notifications.domain;

public enum NotificationKind {

    CONFIRMATION("Consulta agendada", "Sua consulta foi agendada."),
    REMINDER("Lembrete: sua consulta está próxima", "Este é um lembrete da sua consulta.");

    private final String subject;
    private final String opening;

    NotificationKind(String subject, String opening) {
        this.subject = subject;
        this.opening = opening;
    }

    public String subject() {
        return subject;
    }

    public String opening() {
        return opening;
    }
}
