package com.fiap.hospital.identity.activation.service;

import com.fiap.hospital.identity.accounts.contract.ActivatePendingAccount;
import com.fiap.hospital.identity.accounts.contract.ActivationTokens;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivateAccount {

    public static final String INVALID_TOKEN = "activation token is invalid or expired";

    private static final int MIN_PASSWORD_LENGTH = 8;

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
        if (isBlank(token)) {
            throw invalid();
        }
        String clearToken = token.strip();
        validatePassword(clearToken, password);

        Instant now = Instant.now(clock);
        UUID userId = activationTokens.consume(clearToken, now).orElseThrow(this::invalid);
        boolean activated = activatePendingAccount.definePassword(
            userId,
            passwordEncoder.encode(password)
        );
        if (!activated) {
            throw invalid();
        }
    }

    private void validatePassword(String clearToken, String password) {
        if (isBlank(password)) {
            throw new WeakPasswordException("password must not be blank");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new WeakPasswordException(
                "password must be at least " + MIN_PASSWORD_LENGTH + " characters long"
            );
        }
        if (password.equals(clearToken)) {
            throw new WeakPasswordException("password must not match the activation token");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private InvalidActivationTokenException invalid() {
        return new InvalidActivationTokenException();
    }
}
