package com.fiap.hospital.history.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Responde 401 com corpo RFC 7807 (application/problem+json). O entry point
 * padrão do resource server devolve só o header WWW-Authenticate e corpo vazio,
 * o que deixa o cliente sem nenhuma pista do motivo da falha.
 *
 * O header WWW-Authenticate continua obrigatório (RFC 6750): é ele que diz ao
 * cliente qual esquema de autenticação a API espera.
 */
@Component
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    static final String PROBLEM_JSON = "application/problem+json";

    private final JsonMapper mapper;
    private final BearerTokenAuthenticationEntryPoint bearerEntryPoint =
        new BearerTokenAuthenticationEntryPoint();

    public ProblemDetailAuthenticationEntryPoint(JsonMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void commence(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException exception
    ) throws IOException {
        bearerEntryPoint.commence(request, response, exception);
        response.setContentType(PROBLEM_JSON);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatusCode.valueOf(response.getStatus()),
            detail(exception)
        );
        problem.setTitle("Não autenticado");
        problem.setInstance(URI.create(request.getRequestURI()));
        mapper.writeValue(response.getOutputStream(), problem);
    }

    // A descrição do erro do Spring é em inglês ("Jwt expired at ..."); a
    // tradução para a causa raiz em PT-BR acontece aqui, num único lugar.
    private static String detail(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauth2) {
            String description = oauth2.getError().getDescription();
            if (description != null && description.contains("expired")) {
                return "Token expirado. Faça login novamente para obter um novo token.";
            }
            return "Token inválido. Faça login novamente para obter um novo token.";
        }
        return "Token de acesso ausente. Envie o header Authorization: Bearer <token>.";
    }
}
