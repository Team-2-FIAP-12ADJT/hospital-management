package com.fiap.hospital.notification.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// Consumidor de fumaça do ticket 18 — só prova que a cadeia
// outbox->Debezium->Kafka entrega, sem regra de negócio nenhuma.
@Component
public class SmokeConsumer {

    private static final Logger log = LoggerFactory.getLogger(SmokeConsumer.class);

    private final ObjectMapper objectMapper;

    public SmokeConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "hospital.person", groupId = "notification-consumer")
    public void receive(ConsumerRecord<String, String> record) {
        // Payload que não parseia ou envelope sem eventId/eventType não pode nem
        // travar o listener (retry silencioso do error handler default) nem sumir
        // como log vazio (path()/asString() em campo ausente devolve "", não
        // exceção) — os dois já foram achados reais na revisão desta rodada.
        JsonNode envelope;
        try {
            envelope = objectMapper.readTree(record.value());
        } catch (RuntimeException e) {
            log.error(
                "discarding unparseable message on hospital.person, partition={} offset={}: {}",
                record.partition(), record.offset(), e.getMessage()
            );
            return;
        }

        String eventId = envelope.path("eventId").asString();
        String eventType = envelope.path("eventType").asString();
        if (eventId.isEmpty() || eventType.isEmpty()) {
            log.warn(
                "discarding message missing eventId/eventType on hospital.person, "
                    + "partition={} offset={} value={}",
                record.partition(), record.offset(), record.value()
            );
            return;
        }

        log.info("received event eventId={} eventType={}", eventId, eventType);
    }
}
