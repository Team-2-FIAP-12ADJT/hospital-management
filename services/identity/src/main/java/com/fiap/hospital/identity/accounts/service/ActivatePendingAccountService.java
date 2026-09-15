package com.fiap.hospital.identity.accounts.service;

import com.fiap.hospital.identity.accounts.contract.ActivatePendingAccount;
import com.fiap.hospital.identity.accounts.domain.User;
import com.fiap.hospital.identity.accounts.repository.UserRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class ActivatePendingAccountService implements ActivatePendingAccount {

    static final String PENDING_ACTIVATION = "PENDING_ACTIVATION";

    private final UserRepository userRepository;

    ActivatePendingAccountService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean definePassword(UUID userId, String passwordHash) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !PENDING_ACTIVATION.equals(user.getStatus())) {
            return false;
        }
        user.definePassword(passwordHash);
        return true;
    }
}
