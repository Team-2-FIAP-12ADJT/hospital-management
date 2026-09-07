package com.fiap.hospital.identity.activation.service;

import com.fiap.hospital.identity.accounts.contract.ActivatePendingAccount;
import com.fiap.hospital.identity.accounts.contract.ActivationTokens;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ActivateAccount {

    public static final String INVALID_TOKEN = "activation token is invalid or expired";

    private final ActivationTokens activationTokens;
    private final ActivatePendingAccount activatePendingAccount;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public ActivateAccount(
        ActivationTokens activationTokens,
        ActivatePendingAccount activatePendingAccount,
        PasswordEncoder passwordEncoder,
        Clock clock
    ) {
        this.activationTokens = activationTokens;
        this.activatePendingAccount = activatePendingAccount;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public void activate(String token, String password) {
        if (isBlank(token) || isBlank(password)) {
            throw invalid();
        }
        String clearToken = token.strip();
        Instant now = Instant.now(clock);
        UUID userId = activationTokens.findValidUserId(clearToken, now).orElseThrow(this::invalid);
        boolean activated = activatePendingAccount.definePassword(
            userId,
            passwordEncoder.encode(password)
        );
        if (!activated) {
            throw invalid();
        }
        activationTokens.markConsumed(clearToken, now);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_TOKEN);
    }
}
