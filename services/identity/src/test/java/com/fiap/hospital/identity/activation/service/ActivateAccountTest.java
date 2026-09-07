package com.fiap.hospital.identity.activation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fiap.hospital.identity.accounts.contract.ActivatePendingAccount;
import com.fiap.hospital.identity.accounts.contract.ActivationTokens;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ActivateAccountTest {

    private static final Instant NOW = Instant.parse("2026-09-07T16:00:00Z");
    private static final String TOKEN = "3Yb9Qk2Lm7Rx0Tn5";
    private static final String PASSWORD = "s3nha-segura";
    private static final UUID USER_ID = UUID.fromString("3f2b8c10-5d47-4e91-9a2e-7c6f1b0d8e33");

    @Mock
    private ActivationTokens activationTokens;

    @Mock
    private ActivatePendingAccount activatePendingAccount;

    @Mock
    private PasswordEncoder passwordEncoder;

    private ActivateAccount activateAccount;

    @BeforeEach
    void setUp() {
        activateAccount = new ActivateAccount(
            activationTokens,
            activatePendingAccount,
            passwordEncoder,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void consumesTokenAndActivatesAccount() {
        when(activationTokens.findValidUserId(TOKEN, NOW)).thenReturn(Optional.of(USER_ID));
        when(passwordEncoder.encode(PASSWORD)).thenReturn("{bcrypt}hash");
        when(activatePendingAccount.definePassword(USER_ID, "{bcrypt}hash")).thenReturn(true);

        activateAccount.activate(TOKEN, PASSWORD);

        verify(activatePendingAccount).definePassword(USER_ID, "{bcrypt}hash");
        verify(activationTokens).markConsumed(TOKEN, NOW);
    }

    @Test
    void unknownTokenDoesNotRevealExistence() {
        when(activationTokens.findValidUserId(TOKEN, NOW)).thenReturn(Optional.empty());

        assertInvalid(() -> activateAccount.activate(TOKEN, PASSWORD));

        verify(activatePendingAccount, never()).definePassword(any(), any());
        verify(activationTokens, never()).markConsumed(any(), any());
    }

    @Test
    void alreadyActiveAccountDoesNotConsumeToken() {
        when(activationTokens.findValidUserId(TOKEN, NOW)).thenReturn(Optional.of(USER_ID));
        when(passwordEncoder.encode(PASSWORD)).thenReturn("{bcrypt}hash");
        when(activatePendingAccount.definePassword(USER_ID, "{bcrypt}hash")).thenReturn(false);

        assertInvalid(() -> activateAccount.activate(TOKEN, PASSWORD));

        verify(activationTokens, never()).markConsumed(any(), any());
    }

    @Test
    void blankCredentialsAreRejectedTheSameWay() {
        assertInvalid(() -> activateAccount.activate(" ", PASSWORD));
        assertInvalid(() -> activateAccount.activate(TOKEN, " "));
        verify(activationTokens, never()).findValidUserId(any(), any());
    }

    private static void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException error = (ResponseStatusException) ex;
                assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(error.getReason()).isEqualTo(ActivateAccount.INVALID_TOKEN);
            });
    }
}
