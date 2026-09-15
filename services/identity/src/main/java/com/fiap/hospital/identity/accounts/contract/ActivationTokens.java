package com.fiap.hospital.identity.accounts.contract;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ActivationTokens {

    /**
     * Atomically claims the token: consumes it and returns the owning user id
     * only if it was still unused and unexpired. Concurrent callers for the
     * same token never both succeed.
     */
    Optional<UUID> consume(String clearToken, Instant now);
}
