package com.fiap.hospital.history.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    private final JsonMapper mapper;
    private final BearerTokenAccessDeniedHandler bearerAccessDeniedHandler =
        new BearerTokenAccessDeniedHandler();

    public ProblemDetailAccessDeniedHandler(JsonMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void handle(
        HttpServletRequest request,
        HttpServletResponse response,
        AccessDeniedException exception
    ) throws IOException {
        bearerAccessDeniedHandler.handle(request, response, exception);
        response.setContentType(ProblemDetailAuthenticationEntryPoint.PROBLEM_JSON);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatusCode.valueOf(response.getStatus()),
            "Acesso negado. Você não tem permissão para acessar este recurso."
        );
        problem.setTitle("Acesso negado");
        problem.setInstance(URI.create(request.getRequestURI()));
        mapper.writeValue(response.getOutputStream(), problem);
    }
}
