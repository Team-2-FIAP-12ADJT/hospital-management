package com.fiap.hospital.notification.invite;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class AccountEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AccountEventConsumer.class);

    private final ActivationInviteParser parser;
    private final SendActivationInvite sendActivationInvite;

    AccountEventConsumer(
        ActivationInviteParser parser,
        SendActivationInvite sendActivationInvite
    ) {
        this.parser = parser;
        this.sendActivationInvite = sendActivationInvite;
    }

    @KafkaListener(topics = "hospital.account", groupId = "notification-consumer")
    public void receive(ConsumerRecord<String, String> record) {
        consume(record.value(), record.partition(), record.offset());
    }

    void consume(String envelopeJson) {
        consume(envelopeJson, -1, -1L);
    }

    private void consume(String envelopeJson, int partition, long offset) {
        ActivationInvite invite;
        try {
            invite = parser.parse(envelopeJson);
        } catch (UnsupportedAccountEventException ex) {
            log.info("tipo fora do convite de ativação, ignorado");
            return;
        } catch (RuntimeException ex) {
            log.error(
                "discarding unparseable message on hospital.account, partition={} offset={} cause={}",
                partition, offset, ex.getClass().getSimpleName()
            );
            return;
        }

        sendActivationInvite.send(invite);
    }
}
