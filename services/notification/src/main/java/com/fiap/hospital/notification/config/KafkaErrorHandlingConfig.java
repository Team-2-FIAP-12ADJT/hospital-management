package com.fiap.hospital.notification.config;

import java.time.format.DateTimeParseException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.LoggingProducerListener;
import org.springframework.kafka.support.ProducerListener;
import org.springframework.util.backoff.BackOff;
import tools.jackson.core.JacksonException;

/**
 * Retry/DLT wiring shared by every @KafkaListener in this module (hospital.account
 * and hospital.person). Without this, Spring Kafka falls back to the default error
 * handler (FixedBackOff(0, 9)), which exhausts in milliseconds and silently skips
 * the record: a transient failure would drop the message with no idempotency row
 * and no trace.
 */
@Configuration
class KafkaErrorHandlingConfig {

    private static final String DEAD_LETTER_SUFFIX = ".DLT";

    // Broker runs with auto-create-topics disabled, so each DLT must be declared
    // explicitly for KafkaAdmin to create it on boot; it will not appear on its own.
    @Bean
    NewTopic accountEventDeadLetterTopic() {
        return deadLetterTopic("hospital.account");
    }

    @Bean
    NewTopic personEventDeadLetterTopic() {
        return deadLetterTopic("hospital.person");
    }

    @Bean
    NewTopic appointmentEventDeadLetterTopic() {
        return deadLetterTopic("hospital.appointment");
    }

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<?, ?> kafkaTemplate) {
        return errorHandler(kafkaTemplate, backOff());
    }

    /**
     * Fonte unica do handler: recoverer, classificacao de excecao e nivel de log
     * vivem SO aqui. O teste de recuperacao injeta um backoff comprimido por este
     * mesmo metodo em vez de montar um handler proprio — handler de teste que
     * reaplica a classificacao deixaria de acusar a remocao dela na producao.
     */
    static DefaultErrorHandler errorHandler(KafkaTemplate<?, ?> kafkaTemplate, BackOff backOff) {
        DefaultErrorHandler handler = new DefaultErrorHandler(deadLetterRecoverer(kafkaTemplate), backOff);
        // Envelope malformado ou campo com formato invalido e deterministico:
        // repetir so adia a chegada na DLT.
        handler.addNotRetryableExceptions(
            IllegalArgumentException.class,
            DateTimeParseException.class,
            JacksonException.class
        );
        return handler;
    }

    // The DLT destination is derived from the record's own topic, not a fixed
    // constant: this module has more than one @KafkaListener, and a resolver that
    // always points at one DLT would send a poisoned hospital.person message into
    // hospital.account.DLT, sending whoever investigates to the wrong consumer.
    // Every DLT declared above has a single partition, so the resolver always
    // targets partition 0 regardless of how many partitions the source topic has.
    static DeadLetterPublishingRecoverer deadLetterRecoverer(KafkaTemplate<?, ?> kafkaTemplate) {
        return new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            (ConsumerRecord<?, ?> record, Exception ex) ->
                new TopicPartition(record.topic() + DEAD_LETTER_SUFFIX, 0)
        );
    }

    static BackOff backOff() {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(5);
        backOff.setInitialInterval(1000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(30_000L);
        return backOff;
    }

    // Sem este bean, o LoggingProducerListener default do Boot (includeContents=true)
    // grava chave e payload em ERROR quando a publicação em qualquer DLT falha.
    @Bean
    ProducerListener<Object, Object> producerListener() {
        LoggingProducerListener<Object, Object> listener = new LoggingProducerListener<>();
        listener.setIncludeContents(false);
        return listener;
    }

    private static NewTopic deadLetterTopic(String sourceTopic) {
        return TopicBuilder.name(sourceTopic + DEAD_LETTER_SUFFIX).partitions(1).replicas((short) 1).build();
    }
}
