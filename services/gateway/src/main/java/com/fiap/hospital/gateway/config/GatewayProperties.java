package com.fiap.hospital.gateway.config;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway")
public record GatewayProperties(
    Map<String, URI> routes,
    Map<String, URI> healthServices,
    Map<String, URI> apiDocs,
    Duration proxyReadTimeout
) {
}
