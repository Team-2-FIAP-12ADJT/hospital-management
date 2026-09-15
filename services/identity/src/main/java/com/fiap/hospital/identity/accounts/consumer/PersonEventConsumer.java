package com.fiap.hospital.identity.accounts.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Ticket 20: consome {@code hospital.person} e provisiona a conta inativa.
 * Group id próprio — o smoke do notification não compete com este consumidor.
 */
@Component
public class PersonEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PersonEventConsumer.class);

    private final PersonRegistrationParser parser;
    private final ProvisionAccountFromPersonEvent provisionAccount;

    PersonEventConsumer(
        PersonRegistrationParser parser,
        ProvisionAccountFromPersonEvent provisionAccount
    ) {
        this.parser = parser;
        this.provisionAccount = provisionAccount;
    }

    @KafkaListener(topics = "hospital.person", groupId = "identity-consumer")
    public void receive(ConsumerRecord<String, String> record) {
        consume(record.value(), record.partition(), record.offset());
    }

    void consume(String envelopeJson) {
        consume(envelopeJson, -1, -1L);
    }

    private void consume(String envelopeJson, int partition, long offset) {
        PersonRegistration registration;
        try {
            registration = parser.parse(envelopeJson);
        } catch (UnsupportedPersonEventException ex) {
            // O eventType vem do envelope e não é validado contra lista nenhuma:
            // é string arbitrária de quem publica, então não vai para o log.
            log.info(
                "evento de tipo não provisionado, ignorado: partition={} offset={}",
                partition,
                offset
            );
            return;
        }
        provisionAccount.provision(registration);
    }
}
