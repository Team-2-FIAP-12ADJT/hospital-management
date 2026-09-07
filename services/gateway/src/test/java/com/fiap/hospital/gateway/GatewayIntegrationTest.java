package com.fiap.hospital.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import com.fiap.hospital.gateway.proxy.GatewayProxyFilter;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@SpringBootTest(
    classes = GatewayApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@org.springframework.context.annotation.Import(GatewayIntegrationTest.TestSecurityConfiguration.class)
@TestMethodOrder(OrderAnnotation.class)
class GatewayIntegrationTest {

    private static final List<String> SERVICES =
        List.of("identity", "scheduling", "history", "notification");
    private static final Map<String, HttpServer> SERVERS = startServers();
    private static final AtomicBoolean DELAY_HEALTH = new AtomicBoolean();
    private static final AtomicBoolean DELAY_DOCTORS = new AtomicBoolean();
    private static final AtomicBoolean DELAY_BODY = new AtomicBoolean();
    private static final AtomicBoolean DELAY_DOCS = new AtomicBoolean();
    private static final AtomicBoolean DELAY_DOCS_BODY = new AtomicBoolean();
    private static final AtomicBoolean SLOW_IDENTITY = new AtomicBoolean();
    private static final AtomicBoolean SCHEDULING_DOWN = new AtomicBoolean();
    private static final AtomicInteger SCHEDULING_CALLS = new AtomicInteger();
    private static final AtomicReference<String> LAST_PATH = new AtomicReference<>();
    private static final AtomicReference<String> LAST_QUERY = new AtomicReference<>();
    private static final AtomicReference<Map<String, List<String>>> LAST_HEADERS =
        new AtomicReference<>();

    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    private int gatewayPort;

    @BeforeAll
    static void configureRestrictedHeaders() {
        System.setProperty("jdk.httpclient.allowRestrictedHeaders", "connection,host,transfer-encoding");
    }

    @AfterAll
    static void stopServers() {
        SERVERS.values().forEach(server -> server.stop(0));
    }

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) {
        registry.add("SCHEDULING_URL", () -> url("scheduling"));
        registry.add("IDENTITY_URL", () -> url("identity"));
        registry.add("HISTORY_URL", () -> url("history"));
        registry.add("NOTIFICATION_URL", () -> url("notification"));
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
            () -> url("identity") + "/.well-known/jwks.json");
    }

    @Test
    @Order(1)
    void T1_precedenciaEfronteiraDeSegmento() throws Exception {
        var patients = request("POST", "/api/patients", null, Map.of());
        assertThat(patients.statusCode()).isEqualTo(200);
        assertThat(patients.body()).isEqualTo("patients");

        var notRouted = request(
            "POST",
            "/api/patientsXYZ",
            null,
            Map.of("Authorization", "Bearer teste")
        );
        assertThat(notRouted.statusCode()).isEqualTo(403);
        assertThat(SCHEDULING_CALLS).hasValue(1);

        var routedChild = request(
            "POST",
            "/api/appointments/123",
            null,
            Map.of("Authorization", "Bearer teste")
        );
        assertThat(routedChild.statusCode()).isEqualTo(200);
        assertThat(SCHEDULING_CALLS).hasValue(2);
        assertThat(LAST_PATH).hasValue("/api/appointments/123");
    }

    @Test
    @Order(2)
    void T2_transfereStatusEcorpoDoUpstream() throws Exception {
        var response = request("POST", "/api/patients?notFound=true", null, Map.of());
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).isEqualTo("missing");
    }

    @Test
    @Order(3)
    void T3_removeHeadersHopByHopEpreservaAuthorization() throws Exception {
        assertThat(requestRawChunked()).isEqualTo(200);
        assertThat(LAST_HEADERS.get().keySet())
            .noneMatch(name -> List.of(
                "connection", "x-lixo", "x-outro", "transfer-encoding"
            ).contains(name.toLowerCase()));
        assertThat(LAST_HEADERS.get().get("Host"))
            .doesNotContain("cliente.example");
        assertThat(LAST_HEADERS.get().get("Authorization"))
            .containsExactly("Basic dXNlcjpwYXNz");
    }

    @Test
    @Order(4)
    void T4_preservaQueryStringSemDuploEncode() throws Exception {
        var response = request(
            "POST",
            "/api/doctors?nome=a%20b",
            null,
            Map.of("Authorization", "Bearer teste")
        );
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(LAST_QUERY).hasValue("nome=a%20b");
    }

    @Test
    @Order(5)
    void T5_conexaoRecusadaVira503() throws Exception {
        SCHEDULING_DOWN.set(true);
        SERVERS.get("scheduling").stop(0);
        try {
            var response = request(
                "POST", "/api/doctors", null, Map.of("Authorization", "Bearer teste")
            );
            assertThat(response.statusCode()).isEqualTo(503);
        } finally {
            replaceServer("scheduling");
            SCHEDULING_DOWN.set(false);
        }
    }

    @Test
    @Order(6)
    void T6_timeoutDeLeituraVira504() throws Exception {
        DELAY_DOCTORS.set(true);
        try {
            var response = request(
                "POST", "/api/doctors", null, Map.of("Authorization", "Bearer teste")
            );
            assertThat(response.statusCode()).isEqualTo(504);
        } finally {
            DELAY_DOCTORS.set(false);
        }
    }

    @Test
    @Order(7)
    void T7_healthComTodosOsServicosDePe() throws Exception {
        var response = request("GET", "/health/system", null, Map.of());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(
            "\"identity\":\"UP\"",
            "\"scheduling\":\"UP\"",
            "\"history\":\"UP\"",
            "\"notification\":\"UP\""
        );
    }

    @Test
    @Order(8)
    void T8_healthComUmServicoForaEParalelo() throws Exception {
        DELAY_HEALTH.set(true);
        SERVERS.get("notification").stop(0);
        try {
            var started = System.nanoTime();
            var response = request("GET", "/health/system", null, Map.of());
            var elapsedMillis = (System.nanoTime() - started) / 1_000_000;

            assertThat(response.statusCode()).isEqualTo(503);
            assertThat(response.body()).contains("\"notification\":\"DOWN\"");
            assertThat(elapsedMillis).isLessThan(5_000);
        } finally {
            DELAY_HEALTH.set(false);
            replaceServer("notification");
        }
    }

    @Test
    @Order(9)
    void T9_rotaProtegidaRecusaAntesDoUpstream() throws Exception {
        var callsBefore = SCHEDULING_CALLS.get();
        var response = request("POST", "/api/doctors", null, Map.of());
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(SCHEDULING_CALLS).hasValue(callsBefore);
    }

    @Test
    @Order(10)
    void T10_caminhoNaoRoteadoSegueParaOGateway() throws Exception {
        var response = request("GET", "/actuator/health", null, Map.of());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    @Test
    void T11_proxyPreservaCorpoComprimido() throws Exception {
        var response = requestBytes(
            "POST",
            "/api/appointments/compressed",
            null,
            Map.of("Authorization", "Bearer teste")
        );
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Encoding"))
            .hasValue("gzip");
        assertThat(response.body()).isEqualTo(gzip("compressed"));
    }

    @Test
    void T12_timeoutDeConexaoVira503() throws Exception {
        var response = GatewayProxyFilter.transportFailureStatus(
            new java.net.http.HttpConnectTimeoutException("connect timeout")
        );
        assertThat(response).isEqualTo(503);
    }

    @Test
    void T13_resetDoUpstreamNaoVira500() throws Exception {
        var response = request(
            "POST",
            "/api/appointments/reset",
            null,
            Map.of("Authorization", "Bearer teste")
        );
        assertThat(response.statusCode()).isNotEqualTo(500);
        assertThat(response.statusCode()).isEqualTo(503);
    }

    @Test
    void T14_timeoutDuranteCorpoVira504() throws Exception {
        DELAY_BODY.set(true);
        try {
            var response = request(
                "POST",
                "/api/appointments/body-timeout",
                null,
                Map.of("Authorization", "Bearer teste")
            );
            assertThat(response.statusCode()).isEqualTo(504);
            assertThat(response.headers().firstValue("Content-Encoding")).isEmpty();
        } finally {
            DELAY_BODY.set(false);
        }
    }

    // Abort do upstream dentro da faixa que a margem de 20 por cento cobria: prova que a
    // classificacao separa upstream que desistiu de prazo vencido, e nao arredonda para 504.
    @Test
    void T18_abortDoUpstreamAntesDoPrazoVira503() throws Exception {
        var response = authenticatedRequest(
            "POST",
            "/api/appointments/late-abort",
            null,
            Map.of()
        );
        assertThat(response.statusCode()).isEqualTo(503);
    }

    // O upstream atrasa os cabecalhos e so depois trava no corpo: e o caso em que medir o
    // prazo a partir da leitura do corpo, e nao da emissao da requisicao, classifica errado.
    @Test
    void T17_timeoutComCabecalhosAtrasadosVira504() throws Exception {
        var response = authenticatedRequest(
            "POST",
            "/api/appointments/headers-body-timeout",
            null,
            Map.of()
        );
        assertThat(response.statusCode()).isEqualTo(504);
    }

    @Test
    void T16_removeTodosOsConnectionDaResposta() throws Exception {
        var response = request(
            "POST",
            "/api/appointments/response-hop",
            null,
            Map.of("Authorization", "Bearer teste")
        );
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("X-Resposta-Um")).isEmpty();
        assertThat(response.headers().firstValue("X-Resposta-Dois")).isEmpty();
    }

    @Test
    void T15_healthMantemUpDosServicosRapidos() throws Exception {
        SLOW_IDENTITY.set(true);
        try {
            var response = request("GET", "/health/system", null, Map.of());
            assertThat(response.statusCode()).isEqualTo(503);
            assertThat(response.body()).contains(
                "\"identity\":\"DOWN\"",
                "\"scheduling\":\"UP\"",
                "\"history\":\"UP\"",
                "\"notification\":\"UP\""
            );
        } finally {
            SLOW_IDENTITY.set(false);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"identity", "scheduling", "history", "notification"})
    void docsAgregaCadaServicoConfiguradoSemToken(String service) throws Exception {
        var response = request("GET", "/docs/" + service, null, Map.of());

        assertThat(response.statusCode()).isEqualTo(418);
        assertThat(response.body()).isEqualTo("{\"service\":\"" + service + "\"}");
        assertThat(response.headers().firstValue("Content-Type"))
            .hasValue("application/json");
    }

    @Test
    void docsDeServicoDesconhecidoVira404() throws Exception {
        assertThat(request("GET", "/docs/unknown", null, Map.of()).statusCode())
            .isEqualTo(404);
    }

    @Test
    void docsForaDoArVira503() throws Exception {
        SERVERS.get("identity").stop(0);
        try {
            assertThat(request("GET", "/docs/identity", null, Map.of()).statusCode())
                .isEqualTo(503);
        } finally {
            replaceServer("identity");
        }
    }

    @Test
    void docsLentaVira504() throws Exception {
        DELAY_DOCS.set(true);
        try {
            assertThat(request("GET", "/docs/identity", null, Map.of()).statusCode())
                .isEqualTo(504);
        } finally {
            DELAY_DOCS.set(false);
        }
    }

    @Test
    void docsLentaDuranteCorpoVira504() throws Exception {
        DELAY_DOCS_BODY.set(true);
        try {
            assertThat(request("GET", "/docs/identity", null, Map.of()).statusCode())
                .isEqualTo(504);
        } finally {
            DELAY_DOCS_BODY.set(false);
        }
    }

    @Test
    void locationDoUpstreamViraCaminhoRelativo() throws Exception {
        var response = request("GET", "/graphiql/redirect", null, Map.of());

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location"))
            .hasValue("/graphiql?path=/graphql#fragment");
        assertThat(response.headers().firstValue("X-Upstream-Uri"))
            .hasValue(url("history") + "/graphiql?path=/graphql#fragment");
    }

    @Test
    void locationDeTerceiroPermaneceIntacta() throws Exception {
        var response = request("GET", "/graphiql/third-party", null, Map.of());

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location"))
            .hasValue("https://public.example/elsewhere?x=1#fragment");
    }

    @Test
    void locationProtocolRelativeDoUpstreamViraCaminhoRelativo() throws Exception {
        var response = request("GET", "/graphiql/protocol-relative", null, Map.of());

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location"))
            .hasValue("/graphiql?path=/graphql#fragment");
    }

    @Test
    void locationSoComQueryPermaneceIntacta() throws Exception {
        var response = request("GET", "/graphiql/query-only", null, Map.of());

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location"))
            .hasValue("?path=/graphql");
    }

    @Test
    void locationComCaminhoQueComecaComDuasBarrasNaoMudaOrigem() throws Exception {
        var response = request("GET", "/graphiql/double-slash-path", null, Map.of());

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location"))
            .hasValue("/.//external.example/path");
    }

    @Test
    void locationComHostMaiusculoEReescrita() throws Exception {
        var response = request("GET", "/graphiql/uppercase-host", null, Map.of());

        assertThat(response.headers().firstValue("Location"))
            .hasValue("/graphiql?path=/graphql");
    }

    @Test
    void locationComPortaEquivalenteEReescrita() throws Exception {
        var response = request("GET", "/graphiql/equivalent-port", null, Map.of());

        assertThat(response.headers().firstValue("Location"))
            .hasValue("/graphiql?path=/graphql");
    }

    @Test
    void locationComUserInfoNaoEReescrita() throws Exception {
        var response = request("GET", "/graphiql/user-info", null, Map.of());

        assertThat(response.headers().firstValue("Location"))
            .hasValue(
                "http://user@localhost:" + SERVERS.get("history").getAddress().getPort()
                    + "/graphiql?path=/graphql"
            );
    }

    private HttpResponse<String> request(
        String method,
        String path,
        String body,
        Map<String, String> headers
    ) throws Exception {
        var builder = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + gatewayPort + path));
        headers.forEach(builder::header);
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> authenticatedRequest(
        String method,
        String path,
        String body,
        Map<String, String> headers
    ) throws Exception {
        var authenticatedHeaders = new java.util.HashMap<>(headers);
        authenticatedHeaders.put("Authorization", "Bearer test");
        return request(method, path, body, authenticatedHeaders);
    }

    private HttpResponse<byte[]> requestBytes(
        String method,
        String path,
        String body,
        Map<String, String> headers
    ) throws Exception {
        var builder = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + gatewayPort + path));
        headers.forEach(builder::header);
        builder.method(
            method,
            body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)
        );
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private int requestRawChunked() throws IOException {
        try (var socket = new java.net.Socket("localhost", gatewayPort)) {
            var request = """
                POST /api/patients HTTP/1.1\r
                Host: cliente.example\r
                X-Lixo: remover\r
                X-Outro: remover\r
                Connection: close, X-Lixo\r
                Connection: X-Outro\r
                Transfer-Encoding: chunked\r
                Authorization: Basic dXNlcjpwYXNz\r
                Content-Type: text/plain\r
                \r
                4\r
                body\r
                0\r
                \r
                """;
            socket.getOutputStream().write(request.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            socket.setSoTimeout(2_000);
            var response = new String(
                socket.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.US_ASCII
            );
            return Integer.parseInt(response.substring(9, 12));
        }
    }

    private static Map<String, HttpServer> startServers() {
        var servers = new java.util.LinkedHashMap<String, HttpServer>();
        SERVICES.forEach(service -> {
            try {
                servers.put(service, createServer(service, 0));
            } catch (IOException exception) {
                throw new ExceptionInInitializerError(exception);
            }
        });
        return servers;
    }

    private static HttpServer createServer(String service, int port) throws IOException {
        var server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
        server.createContext("/", exchange -> handle(service, exchange));
        // Sem executor o servidor atende em serie, e um handler que segue dormindo depois de
        // o cliente desistir atrasa a requisicao do teste seguinte, contaminando a medicao.
        server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        return server;
    }

    private static void replaceServer(String service) throws IOException {
        var port = SERVERS.get(service).getAddress().getPort();
        SERVERS.put(service, createServer(service, port));
    }

    private static void handle(String service, HttpExchange exchange) throws IOException {
        if (service.equals("scheduling") && SCHEDULING_DOWN.get()) {
            exchange.close();
            return;
        }
        if (exchange.getRequestURI().getPath().equals("/actuator/health")) {
            if (DELAY_HEALTH.get() || (service.equals("identity") && SLOW_IDENTITY.get())) {
                sleep(2_500);
            }
            respond(exchange, 200, "{\"status\":\"UP\"}");
            return;
        }
        if (exchange.getRequestURI().getPath().equals("/v3/api-docs")) {
            if (DELAY_DOCS.get()) {
                sleep(6_000);
            }
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            if (DELAY_DOCS_BODY.get()) {
                exchange.sendResponseHeaders(418, 0);
                exchange.getResponseBody().write("{\"partial\":true}".getBytes());
                exchange.getResponseBody().flush();
                sleep(6_000);
                exchange.close();
                return;
            }
            respond(exchange, 418, "{\"service\":\"" + service + "\"}");
            return;
        }
        if (service.equals("history")
            && exchange.getRequestURI().getPath().equals("/graphiql/redirect")) {
            exchange.getResponseHeaders().set(
                "Location",
                url("history") + "/graphiql?path=/graphql#fragment"
            );
            exchange.getResponseHeaders().set(
                "X-Upstream-Uri",
                url("history") + "/graphiql?path=/graphql#fragment"
            );
            respond(exchange, 302, "");
            return;
        }
        if (service.equals("history")
            && exchange.getRequestURI().getPath().equals("/graphiql/third-party")) {
            exchange.getResponseHeaders().set(
                "Location",
                "https://public.example/elsewhere?x=1#fragment"
            );
            respond(exchange, 302, "");
            return;
        }
        if (service.equals("history")
            && exchange.getRequestURI().getPath().equals("/graphiql/protocol-relative")) {
            exchange.getResponseHeaders().set(
                "Location",
                "//localhost:" + SERVERS.get("history").getAddress().getPort()
                    + "/graphiql?path=/graphql#fragment"
            );
            respond(exchange, 302, "");
            return;
        }
        if (service.equals("history")
            && exchange.getRequestURI().getPath().equals("/graphiql/query-only")) {
            exchange.getResponseHeaders().set("Location", "?path=/graphql");
            respond(exchange, 302, "");
            return;
        }
        if (service.equals("history")
            && exchange.getRequestURI().getPath().equals("/graphiql/double-slash-path")) {
            exchange.getResponseHeaders().set(
                "Location",
                url("history") + "//external.example/path"
            );
            respond(exchange, 302, "");
            return;
        }
        if (service.equals("history")
            && exchange.getRequestURI().getPath().equals("/graphiql/uppercase-host")) {
            exchange.getResponseHeaders().set(
                "Location",
                "http://LOCALHOST:" + SERVERS.get("history").getAddress().getPort()
                    + "/graphiql?path=/graphql"
            );
            respond(exchange, 302, "");
            return;
        }
        if (service.equals("history")
            && exchange.getRequestURI().getPath().equals("/graphiql/equivalent-port")) {
            exchange.getResponseHeaders().set(
                "Location",
                "http://localhost:0" + SERVERS.get("history").getAddress().getPort()
                    + "/graphiql?path=/graphql"
            );
            respond(exchange, 302, "");
            return;
        }
        if (service.equals("history")
            && exchange.getRequestURI().getPath().equals("/graphiql/user-info")) {
            exchange.getResponseHeaders().set(
                "Location",
                "http://user@localhost:" + SERVERS.get("history").getAddress().getPort()
                    + "/graphiql?path=/graphql"
            );
            respond(exchange, 302, "");
            return;
        }
        if (service.equals("scheduling")) {
            SCHEDULING_CALLS.incrementAndGet();
            LAST_PATH.set(exchange.getRequestURI().getPath());
            LAST_QUERY.set(exchange.getRequestURI().getRawQuery());
            LAST_HEADERS.set(exchange.getRequestHeaders());
            if (exchange.getRequestURI().getPath().endsWith("/compressed")) {
                respondBytes(exchange, 200, gzip("compressed"), "gzip");
                return;
            }
            if (exchange.getRequestURI().getPath().endsWith("/reset")) {
                exchange.sendResponseHeaders(200, 100);
                exchange.getResponseBody().write("partial".getBytes());
                exchange.close();
                return;
            }
            if (exchange.getRequestURI().getPath().endsWith("/body-timeout")) {
                exchange.getResponseHeaders().set("Content-Encoding", "gzip");
                exchange.sendResponseHeaders(200, 0);
                if (DELAY_BODY.get()) {
                    exchange.getResponseBody().write("partial".getBytes());
                    exchange.getResponseBody().flush();
                    sleep(6_000);
                }
                exchange.getResponseBody().write("complete".getBytes());
                exchange.close();
                return;
            }
            if (exchange.getRequestURI().getPath().endsWith("/late-abort")) {
                // Aborta dentro da faixa que a margem antiga cobria, mas antes de o vigia
                // disparar: 4,1s contra prazo de 5s menos 250ms de antecedencia do vigia.
                exchange.sendResponseHeaders(200, 100);
                exchange.getResponseBody().write("partial".getBytes());
                exchange.getResponseBody().flush();
                sleep(4_100);
                exchange.close();
                return;
            }
            if (exchange.getRequestURI().getPath().endsWith("/headers-body-timeout")) {
                sleep(2_000);
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().write("partial".getBytes());
                exchange.getResponseBody().flush();
                sleep(6_000);
                exchange.close();
                return;
            }
            if (exchange.getRequestURI().getPath().endsWith("/response-hop")) {
                exchange.getResponseHeaders().add("Connection", "X-Resposta-Um");
                exchange.getResponseHeaders().add("Connection", "X-Resposta-Dois");
                exchange.getResponseHeaders().add("X-Resposta-Um", "remover");
                exchange.getResponseHeaders().add("X-Resposta-Dois", "remover");
                respond(exchange, 200, "ok");
                return;
            }
            if (DELAY_DOCTORS.get()
                && exchange.getRequestURI().getPath().equals("/api/doctors")) {
                sleep(6_000);
            }
            if (exchange.getRequestURI().getQuery() != null
                && exchange.getRequestURI().getQuery().contains("notFound=true")) {
                respond(exchange, 404, "missing");
            } else if (exchange.getRequestURI().getPath().equals("/api/patients")) {
                respond(exchange, 200, "patients");
            } else {
                respond(exchange, 200, "scheduling");
            }
            return;
        }
        respond(exchange, 200, service);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        respondBytes(exchange, status, body.getBytes(java.nio.charset.StandardCharsets.UTF_8), null);
    }

    private static void respondBytes(
        HttpExchange exchange,
        int status,
        byte[] bytes,
        String contentEncoding
    ) throws IOException {
        if (contentEncoding != null) {
            exchange.getResponseHeaders().set("Content-Encoding", contentEncoding);
        }
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static byte[] gzip(String value) throws IOException {
        var output = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(output)) {
            gzip.write(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return output.toByteArray();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static String url(String service) {
        return "http://localhost:" + SERVERS.get(service).getAddress().getPort();
    }

    @TestConfiguration
    static class TestSecurityConfiguration {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                .header("alg", "none")
                .claim("sub", "test")
                .issuedAt(java.time.Instant.now())
                .expiresAt(java.time.Instant.now().plusSeconds(60))
                .build();
        }
    }
}
