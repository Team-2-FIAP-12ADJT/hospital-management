package com.fiap.hospital.history.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI historyOpenAPI(
        @Value("${GATEWAY_PUBLIC_URL:http://localhost:8080}") String gatewayPublicUrl
    ) {
        return new OpenAPI().addServersItem(new Server().url(gatewayPublicUrl));
    }
}
