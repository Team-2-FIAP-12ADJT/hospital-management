package com.fiap.hospital.scheduling.participants.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fiap.hospital.scheduling.participants.repository.PatientRepository;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
@Import(
    PatientRegistrationServiceRealConcurrencyTest.RaceDeterminismConfiguration.class
)
class PatientRegistrationServiceRealConcurrencyTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
        "postgres:18-alpine"
    );

    @Autowired
    private PatientRegistrationService service;

    @Autowired
    private JdbcClient jdbcClient;

    @TestConfiguration
    static class RaceDeterminismConfiguration {

        @Bean
        @Primary
        PatientRepository patientRepositoryProxy(
            @Qualifier("patientRepository") PatientRepository delegate
        ) {
            CyclicBarrier barrier = new CyclicBarrier(2);
            InvocationHandler handler = (proxy, method, args) -> {
                Object result;
                try {
                    result = method.invoke(delegate, args);
                } catch (java.lang.reflect.InvocationTargetException e) {
                    Throwable target = e.getTargetException();
                    if (target instanceof RuntimeException runtime) {
                        throw runtime;
                    }
                    if (target instanceof Error error) {
                        throw error;
                    }
                    throw new RuntimeException(target);
                }

                if (method.getName().equals("existsByTaxIdentifier")) {
                    barrier.await(10, TimeUnit.SECONDS);
                }
                return result;
            };
            return (PatientRepository) Proxy.newProxyInstance(
                PatientRepository.class.getClassLoader(),
                new Class<?>[] { PatientRepository.class },
                handler
            );
        }
    }

    @Test
    void concurrent_registration_for_same_tax_identifier_produces_one_success_and_one_conflict()
        throws InterruptedException, ExecutionException {
        String taxIdentifier = "39053344705";
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> futures = new ArrayList<>();

            Callable<Boolean> task = () -> {
                try {
                    service.register(
                        taxIdentifier,
                        "Maria Souza",
                        "maria.souza@hospital.local",
                        "+5511999999999"
                    );
                    return true;
                } catch (ResponseStatusException ex) {
                    assertThat(ex.getStatusCode()).isEqualTo(
                        HttpStatus.CONFLICT
                    );
                    return false;
                }
            };

            futures.add(executor.submit(task));
            futures.add(executor.submit(task));

            int successes = 0;
            for (Future<Boolean> future : futures) {
                if (future.get()) {
                    successes++;
                }
            }

            assertThat(successes).isEqualTo(1);
        }

        assertThat(
            jdbcClient
                .sql(
                    "SELECT count(*) FROM participants.patient WHERE tax_identifier = :taxIdentifier"
                )
                .param("taxIdentifier", taxIdentifier)
                .query(Long.class)
                .single()
        ).isEqualTo(1L);

        assertThat(
            jdbcClient
                .sql(
                    "SELECT count(*) FROM public.outbox_events WHERE type = 'PatientRegistered'"
                )
                .query(Long.class)
                .single()
        ).isEqualTo(1L);
    }
}
