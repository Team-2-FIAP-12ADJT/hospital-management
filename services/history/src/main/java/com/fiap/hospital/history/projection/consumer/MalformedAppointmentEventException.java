package com.fiap.hospital.history.projection.consumer;

/**
 * Envelope que não vira evento: campo ausente, UUID inválido, instante inválido,
 * JSON quebrado. É determinístico — reentregar não muda o resultado — e por isso
 * o error handler o trata como não-retryable, mandando o registro direto para a DLT.
 *
 * <p>Estende {@link IllegalArgumentException} para que a classificação de
 * não-retryable cubra toda a família por um tipo só.
 *
 * <p>A mensagem carrega apenas o campo e a classificação: {@code UUID.fromString} e
 * {@code Instant.parse} repetem a entrada recusada na mensagem deles, e essa entrada
 * é payload do paciente.
 */
class MalformedAppointmentEventException extends IllegalArgumentException {

    MalformedAppointmentEventException(String message) {
        super(message);
    }

    static MalformedAppointmentEventException invalidField(String field, Throwable cause) {
        MalformedAppointmentEventException exception =
                new MalformedAppointmentEventException("campo inválido: " + field);
        exception.setStackTrace(cause.getStackTrace());
        return exception;
    }
}
