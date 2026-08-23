package com.fiap.hospital.identity.accounts.repository;

import com.fiap.hospital.identity.accounts.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByTaxIdentifier(String taxIdentifier);
}
