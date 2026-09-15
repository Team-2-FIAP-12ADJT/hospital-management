package com.fiap.hospital.history.projection.consumer;

class UnsupportedAppointmentEventException extends RuntimeException {

    private final String eventType;

    // eventType vem do envelope, string arbitraria de quem publica: nao entra
    // na mensagem para nao vazar dado de agendamento no log do handler; fica
    // so no campo, acessivel a quem capturar a excecao (ex.: testes).
    UnsupportedAppointmentEventException(String eventType) {
        super("evento de tipo nao projetado");
        this.eventType = eventType;
    }

    String eventType() {
        return eventType;
    }
}
