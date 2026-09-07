package com.fiap.hospital.identity.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@Testcontainers
class OutboxEventWriterTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private OutboxEventWriter writer;

    @Autowired
    private OutboxEventRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JsonMapper mapper;

    @Test
    void gravaEnvelopeComOccurredAtEmMillis() {
        UUID aggregateId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-08-17T14:05:03.123456789Z");

        UUID eventId = new TransactionTemplate(transactionManager).execute(status ->
            writer.append(
                Aggregate.ACCOUNT,
                aggregateId,
                "UserActivationRequested",
                1,
                occurredAt,
                new Data(aggregateId)
            )
        );

        OutboxEvent event = repository.findById(eventId).orElseThrow();
        assertThat(event.topic()).isEqualTo("hospital.account");
        assertThat(event.occurredAt()).isEqualTo(
            Instant.parse("2026-08-17T14:05:03.123Z")
        );
        assertThat(mapper.readTree(event.envelope()).get("occurredAt").asString())
            .isEqualTo("2026-08-17T14:05:03.123Z");
    }

    @Test
    void exigeTransacaoExistente() {
        assertThrows(IllegalTransactionStateException.class, () ->
            writer.append(
                Aggregate.ACCOUNT,
                UUID.randomUUID(),
                "UserActivationRequested",
                1,
                Instant.now(),
                new Data(UUID.randomUUID())
            )
        );
    }

    record Data(UUID userId) {}
}
