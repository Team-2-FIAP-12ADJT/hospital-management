package com.fiap.hospital.gateway.health;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import com.fiap.hospital.gateway.config.GatewayProperties;

@RestController
public class SystemHealthController {

    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(2);

    private final RestClient healthRestClient;
    private final GatewayProperties properties;

    public SystemHealthController(
        @org.springframework.beans.factory.annotation.Qualifier("healthRestClient")
        RestClient healthRestClient,
        GatewayProperties properties
    ) {
        this.healthRestClient = healthRestClient;
        this.properties = properties;
    }

    @GetMapping("/health/system")
    public ResponseEntity<SystemHealthResponse> systemHealth() {
        var statuses = new java.util.concurrent.ConcurrentHashMap<String, String>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = properties.healthServices().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> executor.submit(() -> check(entry.getValue()))
                ));
            var deadline = System.nanoTime() + HEALTH_TIMEOUT.toNanos();
            futures.forEach((service, future) -> {
                try {
                    // Um future concluído já tem diagnóstico confiável, mesmo após o deadline.
                    if (future.isDone()) {
                        statuses.put(service, future.get());
                        return;
                    }
                    var remaining = deadline - System.nanoTime();
                    if (remaining <= 0) {
                        throw new java.util.concurrent.TimeoutException();
                    }
                    statuses.put(service, future.get(remaining, TimeUnit.NANOSECONDS));
                } catch (Exception exception) {
                    future.cancel(true);
                    statuses.put(service, "DOWN");
                }
            });
        }
        var healthy = statuses.values().stream().allMatch("UP"::equals);
        return ResponseEntity.status(healthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
            .body(new SystemHealthResponse(statuses));
    }

    private String check(URI baseUri) {
        var response = healthRestClient.get()
            .uri(baseUri.resolve("/actuator/health"))
            .exchange((request, clientResponse) -> {
                if (!clientResponse.getStatusCode().is2xxSuccessful()) {
                    return "DOWN";
                }
                return "UP";
            });
        return response == null ? "DOWN" : response;
    }

    public record SystemHealthResponse(Map<String, String> services) {
    }
}
