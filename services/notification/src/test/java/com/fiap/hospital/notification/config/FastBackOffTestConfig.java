package com.fiap.hospital.notification.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * Mesmo handler de producao (mesmo recoverer, mesma classificacao de excecao
 * nao-retentavel), so que com backoff comprimido: 5 tentativas de
 * ExponentialBackOffWithMaxRetries a partir de 1s dobrando chegam a 31s de
 * espera real por teste, o que tornaria a suite lenta sem provar nada a mais.
 * Fica em com.fiap.hospital.notification.config de proposito: e o unico pacote
 * de teste com visibilidade para os metodos estaticos package-private de
 * KafkaErrorHandlingConfig, evitando duplicar o recoverer e a lista de
 * excecoes nao-retentaveis nos testes de recovery de cada topico.
 */
@TestConfiguration
public class FastBackOffTestConfig {

    @Bean
    @Primary
    CommonErrorHandler fastKafkaErrorHandler(KafkaTemplate<?, ?> kafkaTemplate) {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(5);
        backOff.setInitialInterval(20L);
        backOff.setMultiplier(1.0);
        backOff.setMaxInterval(20L);

        // Handler de PRODUCAO, so com o backoff comprimido: se a classificacao de
        // excecao sair do bean real, estes testes acusam.
        return KafkaErrorHandlingConfig.errorHandler(kafkaTemplate, backOff);
    }
}
