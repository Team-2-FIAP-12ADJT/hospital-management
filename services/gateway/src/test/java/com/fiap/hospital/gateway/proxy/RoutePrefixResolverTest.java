package com.fiap.hospital.gateway.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoutePrefixResolverTest {

    private RoutePrefixResolver resolver;

    @BeforeEach
    void setUp() {
        var routes = new LinkedHashMap<String, URI>();
        routes.put("/api/patients", URI.create("http://scheduling:8080"));
        routes.put("/api/doctors", URI.create("http://scheduling:8080"));
        routes.put("/api/appointments", URI.create("http://scheduling:8080"));
        routes.put("/auth", URI.create("http://identity:8080"));
        routes.put("/.well-known/jwks.json", URI.create("http://identity:8080"));
        routes.put("/graphql", URI.create("http://history:8080"));
        routes.put("/graphiql", URI.create("http://history:8080"));
        resolver = new RoutePrefixResolver(routes);
    }

    @Test
    void resolveRotaExataDePatients() {
        assertThat(resolver.resolve("/api/patients"))
            .get()
            .extracting(RoutePrefixResolver.ResolvedRoute::baseUri)
            .isEqualTo(URI.create("http://scheduling:8080"));
    }

    @Test
    void resolveFilhoDePatients() {
        assertThat(resolver.resolve("/api/patients/123")).isPresent();
    }

    @Test
    void rejeitaColisaoSemFronteiraDeSegmento() {
        assertThat(resolver.resolve("/api/patientsXYZ")).isEmpty();
    }

    @Test
    void rejeitaColisaoSemFronteiraNoGraphql() {
        assertThat(resolver.resolve("/graphqlXYZ")).isEmpty();
    }

    @Test
    void escolheOPrefixoMaisLongo() {
        var overlappingRoutes = Map.of(
            "/api", URI.create("http://short:8080"),
            "/api/patients", URI.create("http://scheduling:8080")
        );
        assertThat(new RoutePrefixResolver(overlappingRoutes).resolve("/api/patients/123"))
            .get()
            .extracting(RoutePrefixResolver.ResolvedRoute::prefix)
            .isEqualTo("/api/patients");
    }

    @Test
    void resolveJwksDoIdentity() {
        assertThat(resolver.resolve("/.well-known/jwks.json"))
            .get()
            .extracting(RoutePrefixResolver.ResolvedRoute::baseUri)
            .isEqualTo(URI.create("http://identity:8080"));
    }
}
