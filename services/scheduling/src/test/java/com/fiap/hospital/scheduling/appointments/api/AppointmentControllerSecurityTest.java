package com.fiap.hospital.scheduling.appointments.api;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fiap.hospital.scheduling.support.JwtTestSupport;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
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
class AppointmentControllerSecurityTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    private static final UUID PATIENT = UUID.fromString("00000000-0000-4000-8000-000000000003");
    private static final UUID DOCTOR = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final AtomicLong SLOT = new AtomicLong(0);
    private static HttpServer jwkServer;
    private static RSAKey signingKey;

    @DynamicPropertySource
    static void registerJwksUri(DynamicPropertyRegistry registry)
        throws JOSEException, IOException {
        if (jwkServer == null) {
            signingKey = JwtTestSupport.newSigningKey("appointment-security-test-key");
            jwkServer = JwtTestSupport.serveJwks(signingKey);
        }
        registry.add(
            "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
            () -> "http://localhost:" + jwkServer.getAddress().getPort()
                + "/.well-known/jwks.json"
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
    void doctor_and_nurse_can_schedule() throws Exception {
        assertStatus("DOCTOR", status().isCreated());
        assertStatus("NURSE", status().isCreated());
    }

    @Test
    void patient_is_forbidden_and_anonymous_is_unauthorized() throws Exception {
        mockMvc.perform(post("/api/appointments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body()))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/appointments")
            .header("Authorization", "Bearer " + token("PATIENT"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body()))
            .andExpect(status().isForbidden());
    }

    @Test
    void invalid_payload_is_bad_request() throws Exception {
        mockMvc.perform(post("/api/appointments")
            .header("Authorization", "Bearer " + token("DOCTOR"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void domain_validation_returns_bad_request() throws Exception {
        String token = token("DOCTOR");

        mockMvc.perform(post("/api/appointments")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(Instant.now().minusSeconds(1))))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/appointments")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(
                PATIENT,
                DOCTOR,
                Instant.now().plusSeconds(8000),
                true,
                ""
            )))
            .andExpect(status().isBadRequest());
    }

    @Test
    void unknown_patient_and_doctor_return_bad_request_separately() throws Exception {
        String token = token("DOCTOR");
        UUID invalidPatient = UUID.randomUUID();
        UUID invalidDoctor = UUID.randomUUID();

        mockMvc.perform(post("/api/appointments")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(
                invalidPatient,
                DOCTOR,
                Instant.now().plusSeconds(8100),
                false,
                null
            )))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/appointments")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(
                PATIENT,
                invalidDoctor,
                Instant.now().plusSeconds(8101),
                false,
                null
            )))
            .andExpect(status().isBadRequest());
    }

    @Test
    void invalid_expired_and_unrecognized_role_tokens_are_rejected() throws Exception {
        String requestBody = body();

        mockMvc.perform(post("/api/appointments")
            .header("Authorization", "Bearer not-a-jwt")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/appointments")
            .header("Authorization", "Bearer " + JwtTestSupport.issueExpiredToken(
                signingKey, UUID.randomUUID(), "DOCTOR"
            ))
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/appointments")
            .header("Authorization", "Bearer " + token("ADMIN"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
            .andExpect(status().isForbidden());
    }

    @Test
    void same_doctor_and_same_instant_returns_conflict_on_second_request()
        throws Exception {
        String token = token("DOCTOR");
        Instant slot = Instant.now().plusSeconds(7200 + SLOT.incrementAndGet());
        String body = body(slot);

        mockMvc.perform(post("/api/appointments")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/appointments")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
            .andExpect(status().isConflict());
    }

    private void assertStatus(String role, org.springframework.test.web.servlet.ResultMatcher matcher)
        throws Exception {
        mockMvc.perform(post("/api/appointments")
            .header("Authorization", "Bearer " + token(role))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body()))
            .andExpect(matcher);
    }

    private static String body() {
        Instant slot = Instant.now().plusSeconds(3600 + SLOT.incrementAndGet());
        return body(slot);
    }

    private static String body(Instant slot) {
        return body(PATIENT, DOCTOR, slot, false, null);
    }

    private static String body(
        UUID patientId,
        UUID doctorId,
        Instant slot,
        boolean fitIn,
        String fitInReason
    ) {
        return """
            {"patientId":"%s","doctorId":"%s","scheduledAt":"%s","fitIn":%s,"fitInReason":%s}
            """.formatted(
                patientId,
                doctorId,
                slot,
                fitIn,
                fitInReason == null ? "null" : "\"" + fitInReason + "\""
            );
    }

    private static String token(String role) throws JOSEException {
        return JwtTestSupport.issueToken(signingKey, UUID.randomUUID(), role);
    }
}
