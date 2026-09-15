package com.fiap.hospital.identity.accounts.contract;

import java.util.UUID;

public interface ActivatePendingAccount {

    boolean definePassword(UUID userId, String passwordHash);
}
