package com.fiap.hospital.history.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ProblemDetailAuthenticationEntryPointTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final ProblemDetailAuthenticationEntryPoint entryPoint =
        new ProblemDetailAuthenticationEntryPoint(mapper);

    @Test
    void missingTokenExplainsHowToAuthenticate() throws IOException {
        MockHttpServletResponse response = commence(
            new InsufficientAuthenticationException("Full authentication is required")
        );

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/problem+json"));
        assertEquals("Bearer", response.getHeader(HttpHeaders.WWW_AUTHENTICATE));

        JsonNode body = mapper.readTree(response.getContentAsByteArray());
        assertEquals(401, body.get("status").asInt());
        assertEquals("Não autenticado", body.get("title").asString());
        assertTrue(body.get("detail").asString().contains("ausente"));
        assertEquals("/graphql", body.get("instance").asString());
    }

    @Test
    void expiredTokenExplainsExpiration() throws IOException {
        MockHttpServletResponse response = commence(
            new InvalidBearerTokenException("Jwt expired at 2026-09-13T00:00:00Z")
        );

        JsonNode body = mapper.readTree(response.getContentAsByteArray());
        assertTrue(body.get("detail").asString().contains("expirado"));

        String header = response.getHeader(HttpHeaders.WWW_AUTHENTICATE);
        assertTrue(header.startsWith("Bearer error=\"invalid_token\""));
        assertTrue(header.contains("error_description=\"Jwt expired at"));
    }

    @Test
    void invalidTokenHasGenericMessage() throws IOException {
        MockHttpServletResponse response = commence(
            new InvalidBearerTokenException("Invalid signature")
        );

        JsonNode body = mapper.readTree(response.getContentAsByteArray());
        assertTrue(body.get("detail").asString().contains("inválido"));
    }

    @Test
    void oauth2ErrorWithoutDescriptionOmitsErrorDescription() throws IOException {
        MockHttpServletResponse response = commence(
            new OAuth2AuthenticationException(new OAuth2Error("invalid_token"))
        );

        String header = response.getHeader(HttpHeaders.WWW_AUTHENTICATE);
        assertEquals("Bearer error=\"invalid_token\"", header);

        JsonNode body = mapper.readTree(response.getContentAsByteArray());
        assertTrue(body.get("detail").asString().contains("inválido"));
    }

    @Test
    void blankDescriptionAlsoOmitsErrorDescription() throws IOException {
        MockHttpServletResponse response = commence(
            new OAuth2AuthenticationException(new OAuth2Error("invalid_token", "  ", null))
        );

        String header = response.getHeader(HttpHeaders.WWW_AUTHENTICATE);
        assertEquals("Bearer error=\"invalid_token\"", header);
        assertFalse(header.contains("error_description"));
    }

    private MockHttpServletResponse commence(AuthenticationException exception) throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/graphql");
        MockHttpServletResponse response = new MockHttpServletResponse();
        entryPoint.commence(request, response, exception);
        return response;
    }
}
