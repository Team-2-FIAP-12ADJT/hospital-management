package com.fiap.hospital.identity.accounts.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@Testcontainers
class IdempotencyServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private IdempotencyService service;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void processesSameEventOnlyOnce() {
        UUID eventId = UUID.randomUUID();

        service.process(eventId, () -> {});
        service.process(eventId, () -> {
            throw new IllegalStateException("não deveria rodar na duplicata");
        });

        assertThat(count(eventId)).isOne();
    }

    @Test
    void rollsBackProcessedEventWhenEffectFails() {
        UUID eventId = UUID.randomUUID();

        assertThatThrownBy(() ->
            service.process(eventId, () -> {
                throw new IllegalStateException("effect failed");
            })
        ).isInstanceOf(IllegalStateException.class);

        assertThat(count(eventId)).isZero();
    }

    private long count(UUID eventId) {
        return jdbcClient
            .sql("SELECT count(*) FROM processed_event WHERE event_id = :eventId")
            .param("eventId", eventId)
            .query(Long.class)
            .single();
    }
}
