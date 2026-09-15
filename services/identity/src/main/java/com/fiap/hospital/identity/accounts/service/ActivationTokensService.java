package com.fiap.hospital.identity.accounts.service;

import com.fiap.hospital.identity.accounts.contract.ActivationTokens;
import com.fiap.hospital.identity.accounts.domain.ActivationTokenHash;
import com.fiap.hospital.identity.accounts.repository.ActivationTokenRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ActivationTokensService implements ActivationTokens {

    private final ActivationTokenRepository activationTokenRepository;

    ActivationTokensService(ActivationTokenRepository activationTokenRepository) {
        this.activationTokenRepository = activationTokenRepository;
    }

    // Sob READ COMMITTED (o isolamento configurado) o lock pessimista faz a
    // transação perdedora esperar em vez de falhar por conflito, mas ainda
    // pode estourar por timeout de lock ou por deadlock detectado. Sob
    // REPEATABLE READ/SERIALIZABLE soma-se a esses o conflito de
    // serialização (SQLState 40001). Nenhum dos três é tratado aqui, e por
    // motivos diferentes: timeout e deadlock são falha transitória de
    // infraestrutura, não "token indisponível" — mascarar como 400
    // esconderia contenção real do cliente, que deveria poder repetir a
    // chamada. O conflito de serialização é consequência direta da corrida
    // entre duas ativações e o tratamento correto seria repetir a transação
    // inteira, o que exige um chamador não-transacional em volta de
    // ActivateAccount.activate() para reabrir transação nova — algo que não
    // existe hoje. Até essa reestruturação acontecer, os três propagam em
    // vez de virar um 400 que mentiria sobre a causa.
    @Override
    @Transactional
    public Optional<UUID> consume(String clearToken, Instant now) {
        return activationTokenRepository.findByTokenHashForUpdate(ActivationTokenHash.of(clearToken))
            .filter(token -> token.isUnusedAndValidAt(now))
            .map(token -> {
                token.consume(now);
                return token.getUserId();
            });
    }
}
