package com.fiap.hospital.history.projection.consumer;

class UnsupportedAppointmentEventException extends RuntimeException {

    UnsupportedAppointmentEventException(String eventType) {
        super(eventType);
    }
}
