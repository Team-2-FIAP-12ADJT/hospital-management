package com.fiap.hospital.history.projection.api;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
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
    void patient_with_seeded_subject_receives_nine_appointments() throws Exception {
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
    void patient_ignores_requested_patientId_and_sees_only_own_rows() throws Exception {
        UUID patientId = UUID.fromString(
            "00000000-0000-4000-8000-000000000002"
        );
        String token = issueToken(patientId, "PATIENT");
        String query = "{ appointments(patientId: \"%s\", page: 1, size: 10) { totalElements appointments { patientId } } }"
            .formatted(SEEDED_PATIENT_ID);

        mockMvc
            .perform(
                post("/graphql")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(graphqlBody(query))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.appointments.totalElements").value(5))
            .andExpect(
                jsonPath("$.data.appointments.appointments[*].patientId")
                    .value(everyItem(is(patientId.toString())))
            )
            .andExpect(
                jsonPath("$.data.appointments.appointments").value(hasSize(5))
            );
    }

    @Test
    void doctor_reads_requested_patient_history() throws Exception {
        String token = issueToken(UUID.randomUUID(), "DOCTOR");
        String query = "{ appointments(patientId: \"%s\", page: 1, size: 10) { totalElements appointments { patientId } } }"
            .formatted(SEEDED_PATIENT_ID);

        mockMvc
            .perform(
                post("/graphql")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(graphqlBody(query))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.appointments.totalElements").value(9))
            .andExpect(
                jsonPath("$.data.appointments.appointments[*].patientId")
                    .value(everyItem(is(SEEDED_PATIENT_ID.toString())))
            )
            .andExpect(
                jsonPath("$.data.appointments.appointments").value(hasSize(9))
            );
    }

    @Test
    void nurse_reads_requested_patient_history() throws Exception {
        String token = issueToken(UUID.randomUUID(), "NURSE");
        String query = "{ appointments(patientId: \"%s\", page: 1, size: 10) { totalElements appointments { patientId } } }"
            .formatted(SEEDED_PATIENT_ID);

        mockMvc
            .perform(
                post("/graphql")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(graphqlBody(query))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.appointments.totalElements").value(9))
            .andExpect(
                jsonPath("$.data.appointments.appointments[*].patientId")
                    .value(everyItem(is(SEEDED_PATIENT_ID.toString())))
            )
            .andExpect(
                jsonPath("$.data.appointments.appointments").value(hasSize(9))
            );
    }

    @Test
    void doctor_without_patientId_receives_bad_request() throws Exception {
        String token = issueToken(UUID.randomUUID(), "DOCTOR");

        mockMvc
            .perform(
                post("/graphql")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(graphqlBody("{ appointments(page: 1, size: 10) { totalElements } }"))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.appointments").doesNotExist())
            .andExpect(jsonPath("$.errors[0].extensions.classification").value("BAD_REQUEST"));
    }

    @Test
    void nurse_without_patientId_receives_bad_request() throws Exception {
        String token = issueToken(UUID.randomUUID(), "NURSE");

        mockMvc
            .perform(
                post("/graphql")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(graphqlBody("{ appointments(page: 1, size: 10) { totalElements } }"))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.appointments").doesNotExist())
            .andExpect(jsonPath("$.errors[0].extensions.classification").value("BAD_REQUEST"));
    }

    @Test
    void blank_role_claim_is_denied() throws Exception {
        String token = issueToken(UUID.randomUUID(), "");

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

    @Test
    void expired_token_receives_401() throws Exception {
        String token = JwtTestSupport.issueExpiredToken(
            signingKey,
            UUID.randomUUID(),
            "PATIENT"
        );

        mockMvc
            .perform(
                post("/graphql")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(graphqlBody("{ appointments(page: 1, size: 10) { totalElements } }"))
            )
            .andExpect(status().isUnauthorized());
    }

    @Test
    void token_signed_by_unknown_key_receives_401() throws Exception {
        RSAKey unknownKey = JwtTestSupport.newSigningKey("history-unknown-key");
        String token = JwtTestSupport.issueToken(
            unknownKey,
            UUID.randomUUID(),
            "PATIENT"
        );

        mockMvc
            .perform(
                post("/graphql")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(graphqlBody("{ appointments(page: 1, size: 10) { totalElements } }"))
            )
            .andExpect(status().isUnauthorized());
    }

    @Test
    void token_with_wrong_issuer_receives_401() throws Exception {
        String token = JwtTestSupport.issueTokenWithClaims(
            signingKey,
            UUID.randomUUID(),
            "PATIENT",
            "attacker",
            "hospital-management"
        );

        mockMvc
            .perform(
                post("/graphql")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(graphqlBody("{ appointments(page: 1, size: 10) { totalElements } }"))
            )
            .andExpect(status().isUnauthorized());
    }

    @Test
    void token_with_wrong_audience_receives_401() throws Exception {
        String token = JwtTestSupport.issueTokenWithClaims(
            signingKey,
            UUID.randomUUID(),
            "PATIENT",
            "identity",
            "wrong-audience"
        );

        mockMvc
            .perform(
                post("/graphql")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(graphqlBody("{ appointments(page: 1, size: 10) { totalElements } }"))
            )
            .andExpect(status().isUnauthorized());
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
