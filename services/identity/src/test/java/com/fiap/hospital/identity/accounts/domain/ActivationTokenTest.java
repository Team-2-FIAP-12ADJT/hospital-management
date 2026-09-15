package com.fiap.hospital.identity.accounts.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActivationTokenTest {

    private static final Instant NOW = Instant.parse("2026-09-07T16:00:00Z");

    @Test
    void unusedAndUnexpiredIsValid() {
        assertThat(token(NOW.plusSeconds(60), null).isUnusedAndValidAt(NOW)).isTrue();
    }

    @Test
    void expiredIsInvalidEvenIfUnused() {
        assertThat(token(NOW.minusSeconds(1), null).isUnusedAndValidAt(NOW)).isFalse();
    }

    @Test
    void consumedIsInvalidEvenIfUnexpired() {
        ActivationToken token = token(NOW.plusSeconds(60), NOW.minusSeconds(10));

        assertThat(token.isUnusedAndValidAt(NOW)).isFalse();
    }

    private static ActivationToken token(Instant expiresAt, Instant consumedAt) {
        ActivationToken activation = new ActivationToken(
            UUID.randomUUID(),
            UUID.randomUUID(),
            ActivationTokenHash.of("tok"),
            expiresAt,
            NOW.minusSeconds(120)
        );
        if (consumedAt != null) {
            activation.consume(consumedAt);
        }
        return activation;
    }
}
