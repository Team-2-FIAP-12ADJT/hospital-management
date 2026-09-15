package com.fiap.hospital.identity.accounts.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.fiap.hospital.identity.accounts.idempotency.ProcessedEventRepository;
import com.fiap.hospital.identity.accounts.repository.UserRepository;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@Testcontainers
@Import(ProvisionAccountUniqueConstraintTest.SkipPreCheckConfiguration.class)
class ProvisionAccountUniqueConstraintTest {

    private static final AtomicLong CPF_SEQUENCE = new AtomicLong(70_000_000_000L);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private PersonEventConsumer consumer;

    @Autowired
    @Qualifier("userRepository")
    private UserRepository users;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @TestConfiguration
    static class SkipPreCheckConfiguration {

        @Bean
        @Primary
        UserRepository userRepositoryProxy(@Qualifier("userRepository") UserRepository delegate) {
            InvocationHandler handler = (proxy, method, args) -> {
                if ("findByTaxIdentifier".equals(method.getName())) {
                    return Optional.empty();
                }
                try {
                    return method.invoke(delegate, args);
                } catch (java.lang.reflect.InvocationTargetException ex) {
                    Throwable target = ex.getTargetException();
                    if (target instanceof RuntimeException runtime) {
                        throw runtime;
                    }
                    if (target instanceof Error error) {
                        throw error;
                    }
                    throw new RuntimeException(target);
                }
            };
            return (UserRepository) Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(),
                new Class<?>[] {UserRepository.class},
                handler
            );
        }
    }

    @Test
    void uniqueDeCpfComOutroIdNaoTravaOConsumidor() {
        String taxIdentifier = String.valueOf(CPF_SEQUENCE.incrementAndGet());
        UUID firstEventId = UUID.randomUUID();
        UUID firstPatientId = UUID.randomUUID();
        consumer.consume(PersonEventFixtures.patientRegistered(
            firstEventId, firstPatientId, taxIdentifier
        ));

        UUID secondEventId = UUID.randomUUID();
        UUID secondPatientId = UUID.randomUUID();

        assertThatNoException().isThrownBy(() ->
            consumer.consume(PersonEventFixtures.patientRegistered(
                secondEventId, secondPatientId, taxIdentifier
            ))
        );

        assertThat(users.findById(firstPatientId)).isPresent();
        assertThat(users.findById(secondPatientId)).isEmpty();
        assertThat(users.findByTaxIdentifier(taxIdentifier))
            .get()
            .extracting(user -> user.getId())
            .isEqualTo(firstPatientId);
        assertThat(processedEventRepository.existsById(secondEventId)).isTrue();
    }
}
