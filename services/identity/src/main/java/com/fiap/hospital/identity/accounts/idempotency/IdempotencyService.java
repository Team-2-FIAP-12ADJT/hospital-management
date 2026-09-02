package com.fiap.hospital.identity.accounts.idempotency;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final ProcessedEventRepository repository;

    public IdempotencyService(ProcessedEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void process(UUID eventId, Runnable effect) {
        if (repository.insertIfAbsent(eventId) == 0) {
            log.info("duplicate event ignored eventId={}", eventId);
            return;
        }

        effect.run();
    }
}
