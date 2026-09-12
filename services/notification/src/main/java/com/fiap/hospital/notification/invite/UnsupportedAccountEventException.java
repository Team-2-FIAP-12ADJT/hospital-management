package com.fiap.hospital.notification.invite;

class UnsupportedAccountEventException extends RuntimeException {

    UnsupportedAccountEventException() {
        super("evento ignorado pelo convite de ativação");
    }
}
