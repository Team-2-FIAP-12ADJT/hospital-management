package com.fiap.hospital.identity.accounts.consumer;

import com.fiap.hospital.identity.accounts.idempotency.IdempotencyService;
import com.fiap.hospital.identity.accounts.repository.UserRepository;
import com.fiap.hospital.identity.outbox.OccurredAtSerializer;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
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
    private static final String UNIQUE_VIOLATION_SQLSTATE = "23505";

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

    // Identificação de constraint segue o padrão do repositório: SQLState mais
    // getConstraintName() do org.hibernate.exception.ConstraintViolationException,
    // sem alcançar o PSQLException, que acoplaria o serviço ao driver. Descarta
    // só no match exato; violação não identificada relança, porque users.id vem
    // do evento e pode colidir por concorrência — não é UUID aleatório, para o
    // qual presumir colisão desprezível seria razoável.
    // Limitação conhecida: o extrator do Hibernate no dialeto Postgres
    // reconstrói o nome a partir do texto da mensagem de erro, que muda com
    // lc_messages. Em instância com locale traduzido o nome vem nulo, o descarte
    // deliberado não acontece e o evento se perde pelo caminho de erro do
    // listener — dez tentativas e log, já que este serviço não configura DLT.
    private static boolean isDuplicateTaxIdentifier(DataIntegrityViolationException ex) {
        return ex.getCause() instanceof ConstraintViolationException constraintViolation
            && UNIQUE_VIOLATION_SQLSTATE.equals(constraintViolation.getSQLState())
            && DUPLICATE_TAX_IDENTIFIER_CONSTRAINT.equals(constraintViolation.getConstraintName());
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
