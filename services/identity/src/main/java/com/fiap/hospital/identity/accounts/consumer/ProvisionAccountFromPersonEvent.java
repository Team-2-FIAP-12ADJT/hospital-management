package com.fiap.hospital.identity.accounts.consumer;

import com.fiap.hospital.identity.accounts.idempotency.IdempotencyService;
import com.fiap.hospital.identity.accounts.repository.UserRepository;
import com.fiap.hospital.identity.outbox.OccurredAtSerializer;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.annotation.JsonSerialize;

@Service
class ProvisionAccountFromPersonEvent {

    private static final Logger log = LoggerFactory.getLogger(ProvisionAccountFromPersonEvent.class);

    static final String PENDING_ACTIVATION = "PENDING_ACTIVATION";
    static final Duration ACTIVATION_TTL = Duration.ofHours(24);

    private final IdempotencyService idempotencyService;
    private final UserRepository userRepository;
    private final PersistProvisionedAccount persistProvisionedAccount;

    ProvisionAccountFromPersonEvent(
        IdempotencyService idempotencyService,
        UserRepository userRepository,
        PersistProvisionedAccount persistProvisionedAccount
    ) {
        this.idempotencyService = idempotencyService;
        this.userRepository = userRepository;
        this.persistProvisionedAccount = persistProvisionedAccount;
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
            try {
                persistProvisionedAccount.persist(registration);
            } catch (DataIntegrityViolationException ex) {
                log.warn(
                    "dropping account provisioning for eventId={} personId={} due to unique constraint ({})",
                    registration.eventId(),
                    registration.personId(),
                    ex.getClass().getSimpleName()
                );
            }
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
