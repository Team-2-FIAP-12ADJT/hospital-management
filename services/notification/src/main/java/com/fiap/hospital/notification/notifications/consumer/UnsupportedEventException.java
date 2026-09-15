package com.fiap.hospital.notification.notifications.consumer;

class UnsupportedEventException extends RuntimeException {

    private final String eventType;

    // eventType vem do envelope e nao e validado contra lista nenhuma: string
    // arbitraria de quem publica, entao nao vai para a mensagem/log; fica so
    // no campo, acessivel a quem capturar a excecao (ex.: testes).
    UnsupportedEventException(String eventType) {
        super("evento fora do escopo deste consumidor");
        this.eventType = eventType;
    }

    String eventType() {
        return eventType;
    }
}
