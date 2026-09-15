package com.fiap.hospital.identity.accounts.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fiap.hospital.identity.accounts.idempotency.ProcessedEventRepository;
import com.fiap.hospital.identity.accounts.repository.ActivationTokenRepository;
import com.fiap.hospital.identity.accounts.repository.UserRepository;
import com.fiap.hospital.identity.outbox.OutboxEventRepository;
import jakarta.persistence.EntityManager;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Ticket 32/23 fix: falha ao gravar o activation token ou o outbox, diferente
 * do CPF duplicado, deve propagar (não ser engolida pelo catch de
 * {@link ProvisionAccountFromPersonEvent}) e desfazer a persistência inteira,
 * já que ela roda em REQUIRES_NEW dentro de {@link PersistProvisionedAccount}.
 */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@Testcontainers
@Import(ProvisioningAtomicityIntegrationTest.FailureInjectionConfiguration.class)
class ProvisioningAtomicityIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private PersonEventConsumer consumer;

    @Autowired
    @Qualifier("userRepository")
    private UserRepository userRepository;

    @Autowired
    @Qualifier("activationTokenRepository")
    private ActivationTokenRepository activationTokenRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @AfterEach
    void resetFailureSwitches() {
        FailureInjectionConfiguration.failOnTokenSave.set(false);
        FailureInjectionConfiguration.failOnOutboxSaveAfterFlush.set(false);
    }

    @TestConfiguration
    static class FailureInjectionConfiguration {

        static final AtomicBoolean failOnTokenSave = new AtomicBoolean(false);
        static final AtomicBoolean failOnOutboxSaveAfterFlush = new AtomicBoolean(false);

        @Bean
        @Primary
        ActivationTokenRepository activationTokenRepositoryProxy(
            @Qualifier("activationTokenRepository") ActivationTokenRepository delegate
        ) {
            InvocationHandler handler = (proxy, method, args) -> {
                if ("save".equals(method.getName()) && failOnTokenSave.get()) {
                    throw new DataIntegrityViolationException(
                        "falha proposital ao gravar o token, não relacionada a CPF duplicado"
                    );
                }
                return invokeReal(delegate, method, args);
            };
            return (ActivationTokenRepository) Proxy.newProxyInstance(
                ActivationTokenRepository.class.getClassLoader(),
                new Class<?>[] {ActivationTokenRepository.class},
                handler
            );
        }

        @Bean
        @Primary
        OutboxEventRepository outboxEventRepositoryProxy(
            @Qualifier("outboxEventRepository") OutboxEventRepository delegate,
            EntityManager entityManager
        ) {
            InvocationHandler handler = (proxy, method, args) -> {
                if ("save".equals(method.getName()) && failOnOutboxSaveAfterFlush.get()) {
                    // Persiste de verdade e força o flush antes de falhar: prova
                    // que o rollback desfaz linhas já gravadas no banco, não só
                    // objetos ainda pendentes no contexto de persistência.
                    invokeReal(delegate, method, args);
                    entityManager.flush();
                    throw new DataIntegrityViolationException(
                        "falha proposital após persistir e flushar user, token e outbox"
                    );
                }
                return invokeReal(delegate, method, args);
            };
            return (OutboxEventRepository) Proxy.newProxyInstance(
                OutboxEventRepository.class.getClassLoader(),
                new Class<?>[] {OutboxEventRepository.class},
                handler
            );
        }

        private static Object invokeReal(Object delegate, Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(delegate, args);
            } catch (InvocationTargetException ex) {
                Throwable target = ex.getTargetException();
                if (target instanceof RuntimeException runtime) {
                    throw runtime;
                }
                if (target instanceof Error error) {
                    throw error;
                }
                throw new RuntimeException(target);
            }
        }
    }

    @Test
    void falhaAoGravarTokenAntesDeTokenEOutboxExistiremDesfazUserEMantemEventoReprocessavel() {
        FailureInjectionConfiguration.failOnTokenSave.set(true);

        UUID eventId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        String taxIdentifier = "70000000099";

        assertThatThrownBy(() -> consumer.consume(
            PersonEventFixtures.patientRegistered(eventId, patientId, taxIdentifier)
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(userRepository.findById(patientId))
            .as("user desfeito pelo rollback do REQUIRES_NEW")
            .isEmpty();
        assertThat(activationTokenRepository.countByUserId(patientId))
            .as("token nunca chegou a ser gravado")
            .isZero();
        assertThat(outboxEventRepository.countByAggregateId(patientId))
            .as("outbox nunca chegou a ser gravado")
            .isZero();
        assertThat(processedEventRepository.existsById(eventId))
            .as("evento continua reprocessável quando a falha não é o CPF duplicado")
            .isFalse();
    }

    @Test
    void falhaAposPersistirEFlusharUserTokenEOutboxAindaAssimDesfazOsTres() {
        FailureInjectionConfiguration.failOnOutboxSaveAfterFlush.set(true);

        UUID eventId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        String taxIdentifier = "70000000098";

        assertThatThrownBy(() -> consumer.consume(
            PersonEventFixtures.patientRegistered(eventId, patientId, taxIdentifier)
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(userRepository.findById(patientId))
            .as("user já flushado, mas desfeito pelo rollback do REQUIRES_NEW")
            .isEmpty();
        assertThat(activationTokenRepository.countByUserId(patientId))
            .as("token já flushado, mas desfeito pelo rollback do REQUIRES_NEW")
            .isZero();
        assertThat(outboxEventRepository.countByAggregateId(patientId))
            .as("outbox já flushado, mas desfeito pelo rollback do REQUIRES_NEW")
            .isZero();
        assertThat(processedEventRepository.existsById(eventId))
            .as("evento continua reprocessável quando a falha não é o CPF duplicado")
            .isFalse();
    }
}
