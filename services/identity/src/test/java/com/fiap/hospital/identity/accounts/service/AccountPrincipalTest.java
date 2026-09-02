package com.fiap.hospital.identity.accounts.service;

import com.fiap.hospital.identity.accounts.domain.Role;
import com.fiap.hospital.identity.accounts.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AccountPrincipalTest {

    private static final String PASSWORD_HASH = "{bcrypt}$2a$10$hash";

    private static User activePatient(UUID userId) {
        return new User(userId, "12345678901", "Test User", "test@example.com",
                Role.PATIENT, "ACTIVE", PASSWORD_HASH);
    }

    @Test
    void isEnabledReturnsFalseWhenStatusIsPendingActivation() {
        User user = new User(UUID.randomUUID(), "12345678901", "Test User", "test@example.com",
                Role.PATIENT, "PENDING_ACTIVATION", null);

        AccountPrincipal principal = new AccountPrincipal(user);

        assertFalse(principal.isEnabled());
    }

    @Test
    void isEnabledReturnsTrueWhenStatusIsActive() {
        AccountPrincipal principal = new AccountPrincipal(activePatient(UUID.randomUUID()));

        assertTrue(principal.isEnabled());
    }

    @Test
    void exposesThePasswordHashAsTheCredential() {
        AccountPrincipal principal = new AccountPrincipal(activePatient(UUID.randomUUID()));

        assertEquals(PASSWORD_HASH, principal.getPassword());
    }

    @Test
    void exposesTheTaxIdentifierAsTheUsername() {
        AccountPrincipal principal = new AccountPrincipal(activePatient(UUID.randomUUID()));

        assertEquals("12345678901", principal.getUsername());
    }

    @Test
    void mapsTheRoleToAPrefixedAuthority() {
        User user = new User(UUID.randomUUID(), "39053344705", "Dra. Helena Prado",
                "helena.prado@hospital.local", Role.DOCTOR, "ACTIVE", PASSWORD_HASH);

        AccountPrincipal principal = new AccountPrincipal(user);

        assertEquals(List.of("ROLE_DOCTOR"),
                principal.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
    }

    @Test
    void passesThroughTheUserId() {
        UUID userId = UUID.randomUUID();
        AccountPrincipal principal = new AccountPrincipal(activePatient(userId));

        assertEquals(userId, principal.getId());
    }

    @Test
    void passesThroughTheRole() {
        AccountPrincipal principal = new AccountPrincipal(activePatient(UUID.randomUUID()));

        assertEquals(Role.PATIENT, principal.getRole());
    }
}
