package com.fiap.hospital.gateway.docs;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.fiap.hospital.gateway.config.GatewayProperties;
import com.fiap.hospital.gateway.proxy.GatewayProxyFilter;
import com.fiap.hospital.gateway.proxy.ProxyBodyReader;

@RestController
public class ApiDocsController {

    private final RestClient proxyRestClient;
    private final GatewayProperties properties;
    private final ProxyBodyReader bodyReader;

    public ApiDocsController(
        @org.springframework.beans.factory.annotation.Qualifier("proxyRestClient")
        RestClient proxyRestClient,
        GatewayProperties properties,
        ProxyBodyReader bodyReader
    ) {
        this.proxyRestClient = proxyRestClient;
        this.properties = properties;
        this.bodyReader = bodyReader;
    }

    @GetMapping("/docs/{service}")
    public ResponseEntity<String> apiDocs(@PathVariable String service) {
        URI baseUri = properties.apiDocs().get(service);
        if (baseUri == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            var started = System.nanoTime();
            return proxyRestClient.get()
                .uri(baseUri.resolve("/v3/api-docs"))
                .exchange((request, response) -> {
                    var builder = ResponseEntity.status(response.getStatusCode());
                    var contentType = response.getHeaders().getContentType();
                    if (contentType != null) {
                        builder.contentType(contentType);
                    }
                    var body = new String(
                        bodyReader.read(response.getBody(), started),
                        java.nio.charset.StandardCharsets.UTF_8
                    );
                    return builder.body(body);
                });
        } catch (ResourceAccessException exception) {
            int status = GatewayProxyFilter.transportFailureStatus(exception);
            if (status == 0) {
                throw exception;
            }
            return ResponseEntity.status(status).build();
        }
    }
}
