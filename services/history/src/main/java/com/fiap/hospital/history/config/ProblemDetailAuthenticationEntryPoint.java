package com.fiap.hospital.history.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
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

    public ProblemDetailAuthenticationEntryPoint(JsonMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void commence(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException exception
    ) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, wwwAuthenticate(exception));
        response.setContentType(PROBLEM_JSON);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.UNAUTHORIZED,
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

    private static String wwwAuthenticate(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauth2) {
            OAuth2Error error = oauth2.getError();
            StringBuilder header = new StringBuilder("Bearer error=\"")
                .append(error.getErrorCode())
                .append('"');
            String description = error.getDescription();
            if (description != null && !description.isBlank()) {
                // Aspas dentro da descrição quebrariam a sintaxe do header (RFC 6750, quoted-string).
                header.append(", error_description=\"")
                    .append(description.replace("\"", "'"))
                    .append('"');
            }
            return header.toString();
        }
        return "Bearer";
    }
}
