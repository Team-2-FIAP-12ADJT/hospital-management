package com.fiap.hospital.identity.accounts.service;

import com.fiap.hospital.identity.accounts.contract.ActivationTokens;
import com.fiap.hospital.identity.accounts.domain.ActivationToken;
import com.fiap.hospital.identity.accounts.domain.ActivationTokenHash;
import com.fiap.hospital.identity.accounts.repository.ActivationTokenRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class ActivationTokensService implements ActivationTokens {

    private final ActivationTokenRepository activationTokenRepository;

    ActivationTokensService(ActivationTokenRepository activationTokenRepository) {
        this.activationTokenRepository = activationTokenRepository;
    }

    @Override
    public Optional<UUID> findValidUserId(String clearToken, Instant now) {
        return activationTokenRepository.findByTokenHash(ActivationTokenHash.of(clearToken))
            .filter(token -> token.isUnusedAndValidAt(now))
            .map(ActivationToken::getUserId);
    }

    @Override
    public void markConsumed(String clearToken, Instant now) {
        activationTokenRepository.findByTokenHash(ActivationTokenHash.of(clearToken))
            .ifPresent(token -> token.consume(now));
    }
}
