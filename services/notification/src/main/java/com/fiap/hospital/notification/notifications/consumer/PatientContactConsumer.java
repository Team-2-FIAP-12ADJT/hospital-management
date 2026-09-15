package com.fiap.hospital.notification.notifications.consumer;

import com.fiap.hospital.notification.notifications.service.MaintainContactReplica;
import com.fiap.hospital.notification.notifications.service.PatientContact;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PatientContactConsumer {

    private static final Logger log = LoggerFactory.getLogger(PatientContactConsumer.class);

    private final PatientContactParser parser;
    private final MaintainContactReplica maintainContactReplica;

    PatientContactConsumer(
        PatientContactParser parser,
        MaintainContactReplica maintainContactReplica
    ) {
        this.parser = parser;
        this.maintainContactReplica = maintainContactReplica;
    }

    @KafkaListener(topics = "hospital.person", groupId = "notification-contact")
    public void receive(ConsumerRecord<String, String> record) {
        consume(record.value(), record.partition(), record.offset());
    }

    void consume(String envelopeJson) {
        consume(envelopeJson, -1, -1L);
    }

    private void consume(String envelopeJson, int partition, long offset) {
        PatientContact contact;
        try {
            contact = parser.parse(envelopeJson);
        } catch (UnsupportedEventException exception) {
            // O eventType vem do envelope e não é validado contra lista nenhuma:
            // é string arbitrária de quem publica, então não vai para o log.
            log.info(
                "evento fora da réplica de contato, ignorado: partition={} offset={}",
                partition,
                offset
            );
            return;
        }
        maintainContactReplica.apply(contact);
    }
}
