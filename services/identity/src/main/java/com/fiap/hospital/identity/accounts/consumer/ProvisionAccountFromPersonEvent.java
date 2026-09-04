package com.fiap.hospital.identity.accounts.consumer;

import com.fiap.hospital.identity.accounts.domain.User;
import com.fiap.hospital.identity.accounts.idempotency.IdempotencyService;
import com.fiap.hospital.identity.accounts.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
class ProvisionAccountFromPersonEvent {

    private static final Logger log = LoggerFactory.getLogger(ProvisionAccountFromPersonEvent.class);

    static final String PENDING_ACTIVATION = "PENDING_ACTIVATION";

    private final IdempotencyService idempotencyService;
    private final UserRepository userRepository;

    ProvisionAccountFromPersonEvent(
        IdempotencyService idempotencyService,
        UserRepository userRepository
    ) {
        this.idempotencyService = idempotencyService;
        this.userRepository = userRepository;
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
            userRepository.save(new User(
                registration.personId(),
                registration.taxIdentifier(),
                registration.name(),
                registration.email(),
                registration.role(),
                PENDING_ACTIVATION,
                null
            ));
        });
    }
}
