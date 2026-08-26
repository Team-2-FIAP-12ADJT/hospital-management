package com.fiap.hospital.identity.accounts.service;

import com.fiap.hospital.identity.accounts.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AccountUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AccountUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String cpf) throws UsernameNotFoundException {
        return userRepository.findByTaxIdentifier(cpf)
                .map(AccountPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with tax identifier: " + cpf));
    }
}
