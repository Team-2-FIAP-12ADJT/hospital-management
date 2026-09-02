package com.fiap.hospital.notification.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;
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
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
        "postgres:18-alpine"
    );

    @Autowired
    private IdempotencyService service;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void processesSameEventOnlyOnce() {
        UUID eventId = UUID.randomUUID();
        AtomicInteger effects = new AtomicInteger();

        service.process(eventId, effects::incrementAndGet);
        service.process(eventId, effects::incrementAndGet);

        assertThat(effects).hasValue(1);
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

    @Test
    void concurrentCallsProcessSameEventOnlyOnce()
        throws InterruptedException, ExecutionException {
        UUID eventId = UUID.randomUUID();
        AtomicInteger effects = new AtomicInteger();
        // Barrier BEFORE calling process(), not after: forces both threads
        // into the INSERT ... ON CONFLICT DO NOTHING at nearly the same
        // instant, exercising real Postgres row-lock contention instead of
        // just proving two sequential calls behave correctly (which the
        // processesSameEventOnlyOnce test above already covers).
        CyclicBarrier start = new CyclicBarrier(2);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    service.process(eventId, effects::incrementAndGet);
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        }

        assertThat(effects).hasValue(1);
        assertThat(count(eventId)).isOne();
    }

    private long count(UUID eventId) {
        return jdbcClient
            .sql("SELECT count(*) FROM processed_event WHERE event_id = :eventId")
            .param("eventId", eventId)
            .query(Long.class)
            .single();
    }
}
