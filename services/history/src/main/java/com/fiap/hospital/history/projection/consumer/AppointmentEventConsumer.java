package com.fiap.hospital.history.projection.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AppointmentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AppointmentEventConsumer.class);

    private final AppointmentEventParser parser;
    private final ApplyAppointmentEvent applyEvent;

    AppointmentEventConsumer(
            AppointmentEventParser parser,
            ApplyAppointmentEvent applyEvent
    ) {
        this.parser = parser;
        this.applyEvent = applyEvent;
    }

    @KafkaListener(
            topics = "${history.kafka.appointment-topic}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void receive(ConsumerRecord<String, String> record) {
        consume(record.value(), record.partition(), record.offset());
    }

    void consume(String envelopeJson) {
        consume(envelopeJson, -1, -1L);
    }

    private void consume(String envelopeJson, int partition, long offset) {
        AppointmentEvent message;
        try {
            message = parser.parse(envelopeJson);
        } catch (UnsupportedAppointmentEventException ex) {
            // O eventType vem do envelope e não é validado contra lista nenhuma:
            // é string arbitrária de quem publica, então não vai para o log.
            log.info(
                    "evento de tipo não projetado, ignorado: partition={} offset={}",
                    partition,
                    offset
            );
            return;
        }
        applyEvent.apply(message);
    }
}
