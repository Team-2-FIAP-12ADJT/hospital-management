package com.fiap.hospital.scheduling.appointments.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fiap.hospital.scheduling.appointments.domain.Appointment;
import com.fiap.hospital.scheduling.appointments.repository.AppointmentRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class AppointmentSchedulingTimeIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    private static final UUID PATIENT = UUID.fromString("00000000-0000-4000-8000-000000000003");
    private static final UUID DOCTOR = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Autowired
    private AppointmentSchedulingService service;

    @Autowired
    private AppointmentRepository repository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private MutableClock clock;

    @BeforeEach
    void clean() {
        repository.deleteAll();
        clock.set(Instant.parse("2030-01-01T15:00:00Z"));
    }

    // cancelledAt tem de ser o instante do relogio do servico, nao "algum instante".
    // So com Clock controlado da para assertar o valor exato.
    @Test
    void cancellation_records_the_exact_clock_instant() {
        Appointment appointment = service.schedule(
            PATIENT, DOCTOR, Instant.parse("2030-01-01T16:00:00Z"), false, null
        );
        Instant cancelledAt = Instant.parse("2030-01-01T15:30:00.654321Z");
        clock.set(cancelledAt);

        service.cancel(appointment.getId());

        assertThat(repository.findById(appointment.getId()).orElseThrow().getCancelledAt())
            .isEqualTo(cancelledAt);
    }

    @Test
    void lock_wait_is_followed_by_new_time_validation() throws Exception {
        Instant slot = Instant.parse("2030-01-01T15:00:01Z");
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> holder = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                repository.acquireDoctorScheduleLock(DOCTOR);
                lockAcquired.countDown();
                await(releaseLock);
            }));

            assertThat(lockAcquired.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> scheduled = executor.submit(() ->
                service.schedule(PATIENT, DOCTOR, slot, false, null)
            );
            assertThatThrownBy(() -> scheduled.get(200, TimeUnit.MILLISECONDS))
                .isInstanceOf(TimeoutException.class);
            clock.set(Instant.parse("2030-01-01T15:00:02Z"));
            releaseLock.countDown();

            assertThatThrownBy(() -> scheduled.get(5, TimeUnit.SECONDS))
                .hasCauseInstanceOf(IllegalArgumentException.class);
            holder.get(5, TimeUnit.SECONDS);
        } finally {
            releaseLock.countDown();
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("timed out waiting for latch");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    @TestConfiguration
    static class Configuration {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> current =
            new AtomicReference<>(Instant.parse("2030-01-01T15:00:00Z"));

        void set(Instant instant) {
            current.set(instant);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current.get();
        }
    }
}
