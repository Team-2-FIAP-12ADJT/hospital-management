package com.fiap.hospital.identity.accounts.consumer;

class UnsupportedPersonEventException extends RuntimeException {

    UnsupportedPersonEventException(String eventType) {
        super("evento ignorado pelo provisionamento: " + eventType);
    }
}
