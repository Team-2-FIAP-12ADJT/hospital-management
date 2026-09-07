package com.fiap.hospital.identity.accounts.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fiap.hospital.identity.accounts.domain.ActivationToken;
import com.fiap.hospital.identity.accounts.domain.Role;
import com.fiap.hospital.identity.accounts.domain.User;
import com.fiap.hospital.identity.accounts.idempotency.IdempotencyService;
import com.fiap.hospital.identity.accounts.repository.ActivationTokenRepository;
import com.fiap.hospital.identity.accounts.repository.UserRepository;
import com.fiap.hospital.identity.outbox.Aggregate;
import com.fiap.hospital.identity.outbox.OutboxEventRepository;
import com.fiap.hospital.identity.outbox.OutboxEventWriter;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@Testcontainers
class ProvisioningAtomicityIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ActivationTokenRepository activationTokenRepository;

    @Autowired
    private OutboxEventWriter outboxEventWriter;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @Test
    void falhaNoMeioDoLambdaFazRollbackDeUserTokenEOutbox() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        RuntimeException failure = new RuntimeException("falha proposital");

        assertThatThrownBy(() -> idempotencyService.process(eventId, () -> {
            userRepository.save(new User(
                userId, "70000000000", "Teste", "teste@local",
                Role.PATIENT, "PENDING_ACTIVATION", null
            ));
            activationTokenRepository.save(new ActivationToken(
                UUID.randomUUID(), userId, passwordEncoder.encode("token"),
                now.plus(24, ChronoUnit.HOURS), now
            ));
            outboxEventWriter.append(
                Aggregate.ACCOUNT, userId, "UserActivationRequested", 1, now,
                new Data(userId)
            );

            entityManager.flush();
            assertThat(userRepository.findById(userId))
                .as("user visível dentro da transação antes do rollback")
                .isPresent();
            assertThat(activationTokenRepository.countByUserId(userId))
                .as("token visível dentro da transação antes do rollback")
                .isEqualTo(1);
            assertThat(outboxEventRepository.countByAggregateId(userId))
                .as("outbox visível dentro da transação antes do rollback")
                .isEqualTo(1);

            throw failure;
        })).isSameAs(failure);

        new TransactionTemplate(transactionManager).execute(status -> {
            assertThat(userRepository.findById(userId)).isEmpty();
            assertThat(activationTokenRepository.countByUserId(userId)).isZero();
            assertThat(outboxEventRepository.countByAggregateId(userId)).isZero();
            return null;
        });
    }

    record Data(UUID userId) {}
}
