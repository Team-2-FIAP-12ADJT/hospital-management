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
    private final ProxyBodyReader bodyReader;

    public GatewayProxyFilter(
        @org.springframework.beans.factory.annotation.Qualifier("proxyRestClient")
        RestClient proxyRestClient,
        GatewayProperties properties,
        ProxyBodyReader bodyReader
    ) {
        this.proxyRestClient = proxyRestClient;
        this.routeResolver = new RoutePrefixResolver(properties.routes());
        this.bodyReader = bodyReader;
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
        if (containsCause(exception, ProxyBodyReader.ProxyBodyReadTimeoutException.class)) {
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
                    copyResponseHeaders(clientResponse.getHeaders(), response, target);
                    return null;
                }
                // Quem fecha o stream por prazo vencido e este vigia, e so ele levanta a
                // bandeira. Comparar tempo decorrido no catch nao distingue um abort do
                // upstream a poucos milissegundos do prazo de um prazo de fato vencido.
                var bytes = bodyReader.read(clientResponse.getBody(), started);
                copyResponseHeaders(clientResponse.getHeaders(), response, target);
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

    private void copyResponseHeaders(
        HttpHeaders source,
        HttpServletResponse target,
        URI upstreamRequest
    ) {
        var connectionHeaders = new HashSet<String>();
        source.getOrEmpty("Connection").forEach(value ->
            connectionHeaders.addAll(connectionHeaderNames(value))
        );
        source.forEach((name, values) -> {
            var lowerName = name.toLowerCase(Locale.ROOT);
            if (!name.equalsIgnoreCase("Connection")
                && !HOP_BY_HOP.contains(lowerName)
                && !connectionHeaders.contains(lowerName)) {
                values.forEach(value ->
                    target.addHeader(
                        name,
                        lowerName.equals("location")
                            ? rewriteLocation(value, upstreamRequest)
                            : value
                    )
                );
            }
        });
    }

    private String rewriteLocation(String value, URI upstreamRequest) {
        if (value == null) {
            return null;
        }
        try {
            var location = URI.create(value);
            if (location.getRawAuthority() == null) {
                return value;
            }
            var resolved = upstreamRequest.resolve(location);
            if (resolved.isAbsolute()
                && sameOrigin(upstreamRequest, resolved)) {
                var relative = resolved.getRawPath();
                if (relative == null || relative.isEmpty()) {
                    relative = "/";
                }
                if (resolved.getRawQuery() != null) {
                    relative += "?" + resolved.getRawQuery();
                }
                if (resolved.getRawFragment() != null) {
                    relative += "#" + resolved.getRawFragment();
                }
                if (relative.startsWith("//")) {
                    relative = "/.//" + relative.substring(2);
                }
                return relative;
            }
        } catch (IllegalArgumentException ignored) {
            // Preserve an invalid Location rather than changing the upstream response.
        }
        return value;
    }

    private boolean sameOrigin(URI left, URI right) {
        if (left.getHost() == null || right.getHost() == null
            || !left.getScheme().equalsIgnoreCase(right.getScheme())
            || !left.getHost().equalsIgnoreCase(right.getHost())
            || !java.util.Objects.equals(left.getRawUserInfo(), right.getRawUserInfo())) {
            return false;
        }
        return effectivePort(left) == effectivePort(right);
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return switch (uri.getScheme().toLowerCase(Locale.ROOT)) {
            case "http" -> 80;
            case "https" -> 443;
            default -> -1;
        };
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

}
