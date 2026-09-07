package com.fiap.hospital.gateway.proxy;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

public class RoutePrefixResolver {

    private final Map<String, URI> routes;

    public RoutePrefixResolver(Map<String, URI> routes) {
        this.routes = Map.copyOf(routes);
    }

    public Optional<ResolvedRoute> resolve(String path) {
        return routes.entrySet().stream()
            .filter(entry -> matches(entry.getKey(), path))
            .max((left, right) -> Integer.compare(
                left.getKey().length(), right.getKey().length()
            ))
            .map(entry -> new ResolvedRoute(entry.getKey(), entry.getValue()));
    }

    private boolean matches(String prefix, String path) {
        // A fronteira impede que uma rota de /api/patients capture /api/patientsXYZ.
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    public record ResolvedRoute(String prefix, URI baseUri) {
    }
}
