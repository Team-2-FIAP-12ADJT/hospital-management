package com.fiap.hospital.notification.invite;

import java.util.UUID;

/**
 * Envelope íntegro cujo conteúdo é recusado de forma definitiva — hoje só o
 * e-mail, que o parser valida contra injeção de cabeçalho SMTP (CR, LF, vírgula)
 * e contra endereço malformado.
 *
 * <p>Carrega o {@code eventId} porque a recusa é permanente e o tratamento é
 * gravar idempotência e descartar de propósito: sem o identificador não haveria o
 * que gravar, e o evento voltaria a cada replay. O valor recusado **não** entra na
 * mensagem — o payload de {@code UserActivationRequested} carrega o token de
 * ativação em claro.
 *
 * <p>Estende {@link IllegalArgumentException} de propósito: é recusa de argumento,
 * e a classificação de não-retentável do error handler do módulo já cobre esse tipo —
 * se um dia esta exceção escapar do consumidor, ela vai direto para a DLT em vez de
 * percorrer o backoff inteiro.
 */
class RejectedActivationInviteException extends IllegalArgumentException {

    private final transient UUID eventId;
    private final String field;

    RejectedActivationInviteException(UUID eventId, String field) {
        super("campo recusado de forma definitiva: " + field);
        this.eventId = eventId;
        this.field = field;
    }

    UUID eventId() {
        return eventId;
    }

    String field() {
        return field;
    }
}
