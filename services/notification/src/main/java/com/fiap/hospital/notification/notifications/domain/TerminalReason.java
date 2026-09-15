package com.fiap.hospital.notification.notifications.domain;

/**
 * Por que a notificação parou de ser tentada. O estado terminal diz de quem é o
 * veredito — {@code FAILED} sobre o destino, {@code ABANDONED} sobre as nossas
 * tentativas —, mas {@code attempts} não distingue uma transitória que esgotou o
 * teto de uma envenenada que falhou de forma inesperada em toda varredura. Sem
 * este campo a causa existe só no log, que não sobrevive à rotação.
 */
public enum TerminalReason {

    /** Veredito do servidor sobre o destino: bounce 5xx, endereço inválido. */
    PERMANENT_MAIL_FAILURE,

    /** Falha transitória (SMTP 4xx, rede, réplica de contato ausente) que bateu o teto. */
    TRANSIENT_EXHAUSTED,

    /** Exceção inesperada em toda tentativa — a notificação envenena a varredura. */
    POISONED
}
