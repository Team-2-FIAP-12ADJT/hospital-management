package com.fiap.hospital.history.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenApiConfigTest {

    @Test
    void publishesGatewayAsOpenApiServer() {
        var api = new OpenApiConfig().historyOpenAPI("http://localhost:8080");
        assertEquals("http://localhost:8080", api.getServers().getFirst().getUrl());
    }
}
