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
    private final ApplyAppointmentScheduled applyScheduled;

    AppointmentEventConsumer(
            AppointmentEventParser parser,
            ApplyAppointmentScheduled applyScheduled
    ) {
        this.parser = parser;
        this.applyScheduled = applyScheduled;
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
        AppointmentScheduledMessage message;
        try {
            message = parser.parse(envelopeJson);
        } catch (UnsupportedAppointmentEventException ex) {
            log.info("tipo fora da projeção, ignorado: {}", ex.getMessage());
            return;
        } catch (RuntimeException ex) {
            log.error(
                    "discarding unparseable message on hospital.appointment, partition={} offset={}: {}",
                    partition,
                    offset,
                    ex.getMessage()
            );
            return;
        }
        applyScheduled.apply(message);
    }
}
