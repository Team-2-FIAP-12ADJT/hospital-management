package com.fiap.hospital.gateway.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient proxyRestClient(GatewayProperties properties) {
        var client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofMillis(500))
            .build();
        var requestFactory = new JdkClientHttpRequestFactory(client);
        // O proxy não pode descomprimir e depois repassar o Content-Encoding original.
        requestFactory.enableCompression(false);
        // A folga fica do lado do Spring, nao do vigia: assim a resposta valida dispoe do
        // prazo configurado inteiro, e em regra quem fecha o stream por vencimento e o vigia,
        // que sinaliza. Se o agendador do vigia atrasar mais que esta folga, o Spring fecha
        // primeiro sem reivindicar o estado, e quem classifica passa a ser a rede de seguranca
        // por tempo do GatewayProxyFilter, que so age com o prazo ja vencido.
        // O prazo do Spring segue governando a fase de cabecalhos.
        requestFactory.setReadTimeout(properties.proxyReadTimeout().plusMillis(250));
        return RestClient.builder()
            .requestFactory(requestFactory)
            .build();
    }

    @Bean
    public RestClient healthRestClient() {
        var client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofMillis(500))
            .build();
        var requestFactory = new JdkClientHttpRequestFactory(client);
        requestFactory.setReadTimeout(Duration.ofSeconds(2));
        return RestClient.builder()
            .requestFactory(requestFactory)
            .build();
    }

}
