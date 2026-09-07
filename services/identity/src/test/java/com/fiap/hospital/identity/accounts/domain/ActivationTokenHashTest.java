package com.fiap.hospital.identity.accounts.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ActivationTokenHashTest {

    @Test
    void isDeterministicSoTheLookupCanUseTheUniqueIndex() {
        String token = "3Yb9Qk2Lm7Rx0Tn5";

        assertThat(ActivationTokenHash.of(token))
            .isEqualTo(ActivationTokenHash.of(token))
            .hasSize(64)
            .matches("[0-9a-f]{64}");
    }

    @Test
    void differentTokensProduceDifferentHashes() {
        assertThat(ActivationTokenHash.of("a")).isNotEqualTo(ActivationTokenHash.of("b"));
    }
}
