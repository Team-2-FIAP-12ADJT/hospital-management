package com.fiap.hospital.identity.accounts.contract;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ActivationTokens {

    Optional<UUID> findValidUserId(String clearToken, Instant now);

    void markConsumed(String clearToken, Instant now);
}
