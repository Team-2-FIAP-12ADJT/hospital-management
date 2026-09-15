package com.fiap.hospital.identity.accounts.consumer;

import com.fiap.hospital.identity.accounts.idempotency.IdempotencyService;
import com.fiap.hospital.identity.accounts.repository.UserRepository;
import com.fiap.hospital.identity.outbox.OccurredAtSerializer;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
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
    private static final String DUPLICATE_TAX_IDENTIFIER_CONSTRAINT = "uk_users_tax_identifier";

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
                if (!isDuplicateTaxIdentifier(ex)) {
                    throw ex;
                }
                log.warn(
                    "dropping account provisioning for eventId={} personId={} due to unique constraint ({})",
                    registration.eventId(),
                    registration.personId(),
                    DUPLICATE_TAX_IDENTIFIER_CONSTRAINT
                );
            }
        });
    }

    // getConstraintName() do Hibernate reconstrói o nome a partir do texto da
    // mensagem do Postgres, que muda com lc_messages. O campo "Constraint" do
    // protocolo de erro do Postgres (PSQLException.getServerErrorMessage()) é
    // estruturado, não texto — independe de idioma. Só descartamos quando esse
    // campo bate exatamente com a constraint de CPF duplicado; qualquer outra
    // (inclusive sem constraint identificada) relança, porque users.id vem do
    // evento e pode colidir por concorrência — não é uma colisão de UUID
    // aleatório para presumir desprezível.
    private static boolean isDuplicateTaxIdentifier(DataIntegrityViolationException ex) {
        return DUPLICATE_TAX_IDENTIFIER_CONSTRAINT.equals(constraintNameOf(ex));
    }

    private static String constraintNameOf(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof PSQLException psqlException) {
                ServerErrorMessage serverError = psqlException.getServerErrorMessage();
                return serverError == null ? null : serverError.getConstraint();
            }
        }
        return null;
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
