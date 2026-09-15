package com.fiap.hospital.identity.accounts.consumer;

import com.fiap.hospital.identity.accounts.domain.ActivationToken;
import com.fiap.hospital.identity.accounts.domain.ActivationTokenHash;
import com.fiap.hospital.identity.accounts.domain.User;
import com.fiap.hospital.identity.accounts.repository.ActivationTokenRepository;
import com.fiap.hospital.identity.accounts.repository.UserRepository;
import com.fiap.hospital.identity.outbox.Aggregate;
import com.fiap.hospital.identity.outbox.OutboxEventWriter;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class PersistProvisionedAccount {

    private final UserRepository userRepository;
    private final ActivationTokenRepository activationTokenRepository;
    private final OutboxEventWriter outboxEventWriter;
    private final Clock clock;

    PersistProvisionedAccount(
        UserRepository userRepository,
        ActivationTokenRepository activationTokenRepository,
        OutboxEventWriter outboxEventWriter,
        Clock clock
    ) {
        this.userRepository = userRepository;
        this.activationTokenRepository = activationTokenRepository;
        this.outboxEventWriter = outboxEventWriter;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void persist(PersonRegistration registration) {
        User user = userRepository.saveAndFlush(new User(
            registration.personId(),
            registration.taxIdentifier(),
            registration.name(),
            registration.email(),
            registration.role(),
            ProvisionAccountFromPersonEvent.PENDING_ACTIVATION,
            null
        ));

        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
        Instant expiresAt = now.plus(ProvisionAccountFromPersonEvent.ACTIVATION_TTL);
        String activationToken = UUID.randomUUID().toString();
        activationTokenRepository.save(new ActivationToken(
            UUID.randomUUID(),
            user.getId(),
            ActivationTokenHash.of(activationToken),
            expiresAt,
            now
        ));
        // O token em claro fica no payload de outbox_events até o Debezium
        // publicar o evento — quem tiver acesso de leitura à tabela ativa
        // qualquer conta pendente enquanto a linha existir. Purgar a linha
        // exige saber quando o Debezium já a leu, o que este serviço não
        // controla (CDC assíncrono, sem ack); tratar isso pertence ao
        // ticket 22 (e-mail), não a este.
        outboxEventWriter.append(
            Aggregate.ACCOUNT,
            user.getId(),
            "UserActivationRequested",
            1,
            now,
            new ProvisionAccountFromPersonEvent.UserActivationRequestedData(
                user.getId(), user.getName(), user.getEmail(),
                user.getRole().name(), activationToken, expiresAt
            )
        );
    }
}
