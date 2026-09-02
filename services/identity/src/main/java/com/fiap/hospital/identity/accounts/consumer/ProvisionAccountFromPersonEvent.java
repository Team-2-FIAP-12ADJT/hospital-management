package com.fiap.hospital.identity.accounts.consumer;

import com.fiap.hospital.identity.accounts.domain.User;
import com.fiap.hospital.identity.accounts.idempotency.IdempotencyService;
import com.fiap.hospital.identity.accounts.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;

@Service
class ProvisionAccountFromPersonEvent {

    static final String PENDING_ACTIVATION = "PENDING_ACTIVATION";

    private final IdempotencyService idempotencyService;
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    ProvisionAccountFromPersonEvent(
        IdempotencyService idempotencyService,
        UserRepository userRepository,
        EntityManager entityManager
    ) {
        this.idempotencyService = idempotencyService;
        this.userRepository = userRepository;
        this.entityManager = entityManager;
    }

    void provision(PersonRegistration registration) {
        idempotencyService.process(registration.eventId(), () -> {
            if (!userRepository.existsById(registration.personId())) {
                entityManager.persist(new User(
                    registration.personId(),
                    registration.taxIdentifier(),
                    registration.name(),
                    registration.email(),
                    registration.role(),
                    PENDING_ACTIVATION,
                    null
                ));
            }
        });
    }
}
