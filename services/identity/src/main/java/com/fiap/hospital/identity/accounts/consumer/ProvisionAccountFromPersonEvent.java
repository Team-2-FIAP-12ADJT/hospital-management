package com.fiap.hospital.identity.accounts.consumer;

import com.fiap.hospital.identity.accounts.domain.User;
import com.fiap.hospital.identity.accounts.domain.ActivationToken;
import com.fiap.hospital.identity.accounts.idempotency.IdempotencyService;
import com.fiap.hospital.identity.accounts.repository.ActivationTokenRepository;
import com.fiap.hospital.identity.accounts.repository.UserRepository;
import com.fiap.hospital.identity.outbox.Aggregate;
import com.fiap.hospital.identity.outbox.OccurredAtSerializer;
import com.fiap.hospital.identity.outbox.OutboxEventWriter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import tools.jackson.databind.annotation.JsonSerialize;

@Service
class ProvisionAccountFromPersonEvent {

    private static final Logger log = LoggerFactory.getLogger(ProvisionAccountFromPersonEvent.class);

    static final String PENDING_ACTIVATION = "PENDING_ACTIVATION";

    private final IdempotencyService idempotencyService;
    private final UserRepository userRepository;
    private final ActivationTokenRepository activationTokenRepository;
    private final OutboxEventWriter outboxEventWriter;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    ProvisionAccountFromPersonEvent(
        IdempotencyService idempotencyService,
        UserRepository userRepository,
        ActivationTokenRepository activationTokenRepository,
        OutboxEventWriter outboxEventWriter,
        PasswordEncoder passwordEncoder,
        Clock clock
    ) {
        this.idempotencyService = idempotencyService;
        this.userRepository = userRepository;
        this.activationTokenRepository = activationTokenRepository;
        this.outboxEventWriter = outboxEventWriter;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    void provision(PersonRegistration registration) {
        idempotencyService.process(registration.eventId(), () -> {
            if (userRepository.existsById(registration.personId())) {
                return;
            }
            if (userRepository.findByTaxIdentifier(registration.taxIdentifier()).isPresent()) {
                log.warn(
                    "dropping account provisioning for eventId={} personId={} due to taxIdentifier already bound to another account",
                    registration.eventId(),
                    registration.personId()
                );
                return;
            }
            User user = userRepository.save(new User(
                registration.personId(),
                registration.taxIdentifier(),
                registration.name(),
                registration.email(),
                registration.role(),
                PENDING_ACTIVATION,
                null
            ));

            Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
            Instant expiresAt = now.plus(Duration.ofHours(24));
            String activationToken = UUID.randomUUID().toString();
            activationTokenRepository.save(new ActivationToken(
                UUID.randomUUID(),
                user.getId(),
                passwordEncoder.encode(activationToken),
                expiresAt,
                now
            ));
            outboxEventWriter.append(
                Aggregate.ACCOUNT,
                user.getId(),
                "UserActivationRequested",
                1,
                now,
                new UserActivationRequestedData(
                    user.getId(), user.getName(), user.getEmail(),
                    user.getRole().name(), activationToken, expiresAt
                )
            );
        });
    }

    record UserActivationRequestedData(
        UUID userId,
        String name,
        String email,
        String role,
        String activationToken,
        @JsonSerialize(using = OccurredAtSerializer.class)
        Instant expiresAt
    ) {}
}
