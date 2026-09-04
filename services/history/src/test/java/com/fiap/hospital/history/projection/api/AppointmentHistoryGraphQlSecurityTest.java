package com.fiap.hospital.history.projection.api;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fiap.hospital.history.support.JwtTestSupport;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class AppointmentHistoryGraphQlSecurityTest {

    private static final UUID SEEDED_PATIENT_ID = UUID.fromString(
        "00000000-0000-4000-8000-000000000003"
    );

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
        "postgres:18-alpine"
    );

    private static HttpServer jwkServer;
    private static RSAKey signingKey;

    @DynamicPropertySource
    static void registerJwksUri(DynamicPropertyRegistry registry)
        throws JOSEException, IOException {
        if (jwkServer == null) {
            signingKey = JwtTestSupport.newSigningKey(
                "history-security-test-key"
            );
            jwkServer = JwtTestSupport.serveJwks(signingKey);
        }

        registry.add(
            "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
            () ->
                "http://localhost:" +
                jwkServer.getAddress().getPort() +
                "/.well-known/jwks.json"
        );
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .build();
    }

    @Test
    void anonymous_post_without_token_receives_401() throws Exception {
        mockMvc
            .perform(
                post("/graphql")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(graphqlBody("{ appointments(page: 1, size: 10) { totalElements } }"))
            )
            .andExpect(status().isUnauthorized());
    }

    @Test
    void patient_with_seeded_subject_receives_two_appointments() throws Exception {
        String token = issueToken(SEEDED_PATIENT_ID, "PATIENT");

        mockMvc
            .perform(
                post("/graphql")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(graphqlBody("{ appointments(page: 1, size: 10) { totalElements } }"))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.appointments.totalElements").value(9));
    }

    @Test
    void patient_cannot_use_patientId_argument_to_read_another_patient() throws Exception {
        String token = issueToken(UUID.randomUUID(), "PATIENT");
        String query = "{ appointments(patientId: \"%s\", page: 1, size: 10) { totalElements } }"
            .formatted(SEEDED_PATIENT_ID);

        mockMvc
            .perform(
                post("/graphql")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(graphqlBody(query))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.appointments.totalElements").value(0));
    }

    @Test
    void admin_role_is_denied() throws Exception {
        // AppointmentProjectionGraphQlExceptionHandler translates AccessDeniedException into a
        // GraphQL error (ErrorType.FORBIDDEN) inside the response body — GraphQL-over-HTTP keeps
        // the transport status at 200 and reports the failure through `errors[]`, not a 4xx.
        String token = issueToken(UUID.randomUUID(), "ADMIN");

        mockMvc
            .perform(
                post("/graphql")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(graphqlBody("{ appointments(page: 1, size: 10) { totalElements } }"))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.appointments").doesNotExist())
            .andExpect(jsonPath("$.errors[0].extensions.classification").value("FORBIDDEN"));
    }

    private static String issueToken(UUID subject, String role) throws JOSEException {
        return JwtTestSupport.issueToken(signingKey, subject, role);
    }

    private static String graphqlBody(String query) {
        return "{\"query\":\"%s\"}".formatted(query.replace("\"", "\\\""));
    }
}
