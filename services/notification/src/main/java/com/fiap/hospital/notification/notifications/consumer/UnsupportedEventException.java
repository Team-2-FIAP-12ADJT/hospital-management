package com.fiap.hospital.notification.notifications.consumer;

class UnsupportedEventException extends RuntimeException {

    UnsupportedEventException(String eventType) {
        super(eventType);
    }
}
