package com.fiap.hospital.identity.accounts.consumer;

class UnsupportedPersonEventException extends RuntimeException {

    private final String eventType;

    // eventType vem do envelope, string arbitraria de quem publica: nao entra
    // na mensagem para nao vazar dado de pessoa no log do handler; fica so
    // no campo, acessivel a quem capturar a excecao (ex.: testes).
    UnsupportedPersonEventException(String eventType) {
        super("evento ignorado pelo provisionamento");
        this.eventType = eventType;
    }

    String eventType() {
        return eventType;
    }
}
