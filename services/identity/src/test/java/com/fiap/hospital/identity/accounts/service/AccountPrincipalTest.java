package com.fiap.hospital.identity.accounts.service;

import com.fiap.hospital.identity.accounts.domain.Role;
import com.fiap.hospital.identity.accounts.domain.User;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AccountPrincipalTest {

    @Test
    void isEnabledReturnsFalseWhenStatusIsPendingActivation() {
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "12345678901", "Test User", "test@example.com",
                Role.PATIENT, "PENDING_ACTIVATION", null);

        AccountPrincipal principal = new AccountPrincipal(user);

        assertFalse(principal.isEnabled());
    }

    @Test
    void isEnabledReturnsTrueWhenStatusIsActive() {
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "12345678901", "Test User", "test@example.com",
                Role.PATIENT, "ACTIVE", "{bcrypt}$2a$10$hash");

        AccountPrincipal principal = new AccountPrincipal(user);

        assertTrue(principal.isEnabled());
    }
}
