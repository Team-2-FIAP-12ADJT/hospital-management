package com.fiap.hospital.gateway.proxy;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fiap.hospital.gateway.config.GatewayProperties;

@Component
public class GatewayProxyFilter extends OncePerRequestFilter {

    private static final Set<String> HOP_BY_HOP = Set.of(
        "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
        "te", "trailer", "transfer-encoding", "upgrade", "host", "content-length"
    );

    private final RestClient proxyRestClient;
    private final RoutePrefixResolver routeResolver;
    private final long bodyReadTimeoutNanos;


    // Vigia do prazo do corpo: e ele que fecha o stream no vencimento e sinaliza, o que
    // permite classificar por sinal em vez de por tempo observado no catch. Uma thread so
    // agenda; o fechamento, que pode bloquear, sai dela para nao atrasar os demais prazos.
    private final ScheduledThreadPoolExecutor deadlineWatchdog;

    // Estado terminal disputado entre leitura e vigia. Sem a disputa, um abort seguido de
    // suspensao da thread deixaria o vigia marcar prazo vencido depois do fato, virando 504.
    private enum ReadOutcome {
        PENDENTE,
        PRAZO_VENCIDO,
        RESOLVIDO
    }

    public GatewayProxyFilter(
        @org.springframework.beans.factory.annotation.Qualifier("proxyRestClient")
        RestClient proxyRestClient,
        GatewayProperties properties
    ) {
        this.proxyRestClient = proxyRestClient;
        this.routeResolver = new RoutePrefixResolver(properties.routes());
        this.bodyReadTimeoutNanos = properties.proxyReadTimeout().toNanos();
        var scheduler = new ScheduledThreadPoolExecutor(1, Thread.ofVirtual().factory());
        // Sem isto, uma tarefa cancelada fica na fila ate o instante para o qual foi agendada.
        scheduler.setRemoveOnCancelPolicy(true);
        this.deadlineWatchdog = scheduler;
    }

    @jakarta.annotation.PreDestroy
    void encerrarVigia() {
        deadlineWatchdog.shutdownNow();
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        var route = routeResolver.resolve(request.getRequestURI());
        if (route.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            proxy(request, response, route.get());
        } catch (ResourceAccessException exception) {
            writeTransportFailure(exception, response);
        } catch (IOException exception) {
            writeTransportFailure(exception, response);
        }
    }

    private void writeTransportFailure(
        Throwable exception,
        HttpServletResponse response
    ) {
        var status = transportFailureStatus(exception);
        if (status == 0) {
            throw (RuntimeException) exception;
        }
        response.setStatus(status);
    }

    public static int transportFailureStatus(Throwable exception) {
        if (containsCause(exception, ProxyBodyReadTimeoutException.class)) {
            return 504;
        } else if (containsCause(exception, HttpConnectTimeoutException.class)
            || containsCause(exception, ConnectException.class)) {
            return 503;
        } else if (containsCause(exception, HttpTimeoutException.class)
            || containsCause(exception, SocketTimeoutException.class)) {
            return 504;
        } else if (containsCause(exception, IOException.class)) {
            return 503;
        } else {
            return 0;
        }
    }

    private void proxy(
        HttpServletRequest request,
        HttpServletResponse response,
        RoutePrefixResolver.ResolvedRoute route
    ) throws IOException {
        var target = targetUri(request, route);
        var method = HttpMethod.valueOf(request.getMethod());
        var requestBody = request.getInputStream().readAllBytes();
        var started = System.nanoTime();
        proxyRestClient.method(method)
            .uri(target)
            .headers(headers -> copyRequestHeaders(request, headers))
            .body(requestBody)
            .exchange((clientRequest, clientResponse) -> {
                if (clientResponse.getStatusCode().is1xxInformational()
                    || clientResponse.getStatusCode().value() == 204
                    || clientResponse.getStatusCode().value() == 304
                    || method == HttpMethod.HEAD) {
                    response.setStatus(clientResponse.getStatusCode().value());
                    copyResponseHeaders(clientResponse.getHeaders(), response);
                    return null;
                }
                // Quem fecha o stream por prazo vencido e este vigia, e so ele levanta a
                // bandeira. Comparar tempo decorrido no catch nao distingue um abort do
                // upstream a poucos milissegundos do prazo de um prazo de fato vencido.
                var body = clientResponse.getBody();
                var outcome = new AtomicReference<>(ReadOutcome.PENDENTE);
                var remaining = bodyReadTimeoutNanos - (System.nanoTime() - started);
                var watchdog = deadlineWatchdog.schedule(() -> {
                    // So fecha quem vencer a disputa: se a leitura ja se resolveu, o prazo
                    // perdeu e nao pode reclassificar o que aconteceu antes dele.
                    if (outcome.compareAndSet(ReadOutcome.PENDENTE, ReadOutcome.PRAZO_VENCIDO)) {
                        Thread.startVirtualThread(() -> closeQuietly(body));
                    }
                }, Math.max(remaining, 0), TimeUnit.NANOSECONDS);

                byte[] bytes;
                try {
                    bytes = body.readAllBytes();
                    // Perder a disputa aqui significa que o prazo venceu e a leitura so
                    // terminou porque o fechamento e assincrono. Entregar sucesso nesse caso
                    // devolveria ao cliente uma resposta que estourou o prazo.
                    if (!outcome.compareAndSet(ReadOutcome.PENDENTE, ReadOutcome.RESOLVIDO)) {
                        throw new ProxyBodyReadTimeoutException(
                            new IOException("prazo vencido antes da conclusao da leitura")
                        );
                    }
                } catch (IOException exception) {
                    if (!outcome.compareAndSet(ReadOutcome.PENDENTE, ReadOutcome.RESOLVIDO)) {
                        throw new ProxyBodyReadTimeoutException(exception);
                    }
                    // Rede de seguranca para quando o vigia nao chegou a sinalizar, caso do
                    // agendador atrasado com o Spring fechando o stream primeiro. So vale com
                    // o prazo JA vencido: nesse ponto a requisicao estourou o prazo de fato, e
                    // 504 se defende independentemente do que o upstream tenha feito. Nao
                    // confundir com a margem antiga, que disparava a 80% do prazo, antes do
                    // vencimento, e por isso convertia abort legitimo do upstream em 504.
                    if (System.nanoTime() - started >= bodyReadTimeoutNanos) {
                        throw new ProxyBodyReadTimeoutException(exception);
                    }
                    throw exception;
                } finally {
                    watchdog.cancel(false);
                }
                copyResponseHeaders(clientResponse.getHeaders(), response);
                response.setStatus(clientResponse.getStatusCode().value());
                response.getOutputStream().write(bytes);
                return null;
            });
    }

    private URI targetUri(
        HttpServletRequest request,
        RoutePrefixResolver.ResolvedRoute route
    ) {
        var query = request.getQueryString();
        return URI.create(route.baseUri() + request.getRequestURI()
            + (query == null ? "" : "?" + query));
    }

    private void copyRequestHeaders(HttpServletRequest request, HttpHeaders target) {
        var connectionHeaders = new HashSet<String>();
        var names = request.getHeaderNames();
        // A coleta precisa terminar antes da cópia, pois Connection pode vir depois dos headers que nomeia.
        while (names.hasMoreElements()) {
            var name = names.nextElement();
            var values = request.getHeaders(name);
            if (name.equalsIgnoreCase("Connection")) {
                while (values.hasMoreElements()) {
                    connectionHeaders.addAll(connectionHeaderNames(values.nextElement()));
                }
                continue;
            }
        }
        names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            var name = names.nextElement();
            var values = request.getHeaders(name);
            if (name.equalsIgnoreCase("Connection")) {
                continue;
            }
            var lowerName = name.toLowerCase(Locale.ROOT);
            if (!HOP_BY_HOP.contains(lowerName) && !connectionHeaders.contains(lowerName)) {
                while (values.hasMoreElements()) {
                    target.add(name, values.nextElement());
                }
            }
        }
    }

    private void copyResponseHeaders(HttpHeaders source, HttpServletResponse target) {
        var connectionHeaders = new HashSet<String>();
        source.getOrEmpty("Connection").forEach(value ->
            connectionHeaders.addAll(connectionHeaderNames(value))
        );
        source.forEach((name, values) -> {
            var lowerName = name.toLowerCase(Locale.ROOT);
            if (!name.equalsIgnoreCase("Connection")
                && !HOP_BY_HOP.contains(lowerName)
                && !connectionHeaders.contains(lowerName)) {
                values.forEach(value -> target.addHeader(name, value));
            }
        });
    }

    private Set<String> connectionHeaderNames(String value) {
        if (value == null) {
            return Set.of();
        }
        var names = new HashSet<String>();
        for (var name : value.split(",")) {
            names.add(name.trim().toLowerCase(Locale.ROOT));
        }
        return names;
    }

    private static boolean containsCause(Throwable exception, Class<? extends Throwable> type) {
        for (var cause = exception; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }

    private static void closeQuietly(java.io.InputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
            // Fechar e a forma de interromper a leitura travada; falha aqui nao muda o desfecho.
        }
    }

    private static final class ProxyBodyReadTimeoutException extends IOException {

        private ProxyBodyReadTimeoutException(IOException cause) {
            super("Upstream body read timed out", cause);
        }
    }

}
