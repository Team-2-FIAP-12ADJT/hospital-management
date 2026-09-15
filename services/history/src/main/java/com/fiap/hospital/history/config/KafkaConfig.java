package com.fiap.hospital.history.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.core.JacksonException;

import java.time.format.DateTimeParseException;

/**
 * Retry/DLT do consumidor de projeção. Mesmo contrato do
 * {@code KafkaErrorHandlingConfig} do notification: sufixo {@code .DLT}, resolver
 * explícito e o tópico declarado como bean.
 */
@Configuration
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    // O padrão do spring-kafka 4.x é "-dlt". O contrato deste repositório é ".DLT",
    // cravado no resolver do notification — por isso o resolver aqui é explícito:
    // herdar o padrão manda o registro para um tópico que ninguém declara.
    private static final String DEAD_LETTER_SUFFIX = ".DLT";

    // Uma entrega mais duas tentativas: falha transitória de banco se recupera,
    // sem segurar a partição por muito tempo.
    private static final long RETRY_INTERVAL_MS = 1000L;
    private static final long MAX_RETRIES = 2L;

    // O broker roda com auto-create desligado; sem este bean a DLT não existe e a
    // publicação de recuperação falha em silêncio, reentregando para sempre.
    @Bean
    NewTopic appointmentEventDeadLetterTopic(
            @Value("${history.kafka.appointment-topic}") String appointmentTopic
    ) {
        return TopicBuilder.name(appointmentTopic + DEAD_LETTER_SUFFIX)
                .partitions(1)
                .replicas((short) 1)
                .build();
    }

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, String> template) {
        // A DLT declarada acima tem uma partição só, então o destino é sempre a 0.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                template,
                (ConsumerRecord<?, ?> record, Exception exception) ->
                        new TopicPartition(record.topic() + DEAD_LETTER_SUFFIX, 0)
        );

        DefaultErrorHandler handler = new DefaultErrorHandler(
                (record, exception) -> {
                    // Só a classificação: a mensagem da exceção carrega o valor recusado
                    // (UUID e Instant repetem a entrada), e o payload não pode ir para o log.
                    log.error(
                            "dead-lettering record from {}, partition={} offset={} cause={}",
                            record.topic(),
                            record.partition(),
                            record.offset(),
                            NestedExceptionUtils.getMostSpecificCause(exception).getClass().getName()
                    );
                    recoverer.accept(record, exception);
                },
                new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRIES)
        );

        // Envelope inválido é determinístico: repetir só adia o dead-letter.
        // MalformedAppointmentEventException estende IllegalArgumentException e cobre
        // UUID e instante inválidos; DateTimeParseException fica como rede de segurança
        // para qualquer caminho de data que não passe pelo parser.
        handler.addNotRetryableExceptions(
                IllegalArgumentException.class,
                DateTimeParseException.class,
                JacksonException.class
        );

        // O log do framework imprime o registro inteiro, payload incluso.
        handler.setLogLevel(KafkaException.Level.DEBUG);
        return handler;
    }
}
