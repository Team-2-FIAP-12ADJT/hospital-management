package com.fiap.hospital.scheduling.outbox;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

@Component
public class OutboxEventWriter {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventWriter(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    // MANDATORY: falha alto se chamado fora de uma transação existente, em vez de
    // publicar um evento órfão sem o dado de negócio que o originou (ADR-0012).
    @Transactional(propagation = Propagation.MANDATORY)
    public void write(String aggregateType, UUID aggregateId, String eventType, int eventVersion,
                       String topic, Object data) {
        String payload = objectMapper.writeValueAsString(data);
        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(), aggregateType, aggregateId, eventType, eventVersion,
                Instant.now(), payload, topic);
        outboxEventRepository.save(event);
    }
}
