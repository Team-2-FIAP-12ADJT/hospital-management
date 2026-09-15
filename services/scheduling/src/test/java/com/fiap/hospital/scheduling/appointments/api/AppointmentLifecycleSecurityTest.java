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
class AppointmentLifecycleSecurityTest {

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
            signingKey = JwtTestSupport.newSigningKey("appointment-lifecycle-test-key");
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
    void doctorCanRescheduleCancelAndComplete() throws Exception {
        String token = token("DOCTOR");

        mockMvc.perform(post("/api/appointments/" + scheduled(token) + "/reschedule")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(rescheduleBody(nextSlot())))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/appointments/" + scheduled(token) + "/cancel")
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/appointments/" + scheduled(token) + "/complete")
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());
    }

    @Test
    void nurseAlsoRescheduleCancelsAndCompletes() throws Exception {
        String nurse = token("NURSE");

        mockMvc.perform(post("/api/appointments/" + scheduled(nurse) + "/reschedule")
            .header("Authorization", "Bearer " + nurse)
            .contentType(MediaType.APPLICATION_JSON)
            .content(rescheduleBody(nextSlot())))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/appointments/" + scheduled(nurse) + "/cancel")
            .header("Authorization", "Bearer " + nurse))
            .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/appointments/" + scheduled(nurse) + "/complete")
            .header("Authorization", "Bearer " + nurse))
            .andExpect(status().isNoContent());
    }

    @Test
    void anUnrecognizedRoleIsForbiddenOnEveryTransition() throws Exception {
        String appointmentId = scheduled(token("DOCTOR"));
        String admin = token("ADMIN");

        mockMvc.perform(post("/api/appointments/" + appointmentId + "/reschedule")
            .header("Authorization", "Bearer " + admin)
            .contentType(MediaType.APPLICATION_JSON)
            .content(rescheduleBody(nextSlot())))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/appointments/" + appointmentId + "/cancel")
            .header("Authorization", "Bearer " + admin))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/appointments/" + appointmentId + "/complete")
            .header("Authorization", "Bearer " + admin))
            .andExpect(status().isForbidden());
    }

    @Test
    void patientIsForbiddenAndAnonymousIsUnauthorized() throws Exception {
        String appointmentId = scheduled(token("DOCTOR"));

        mockMvc.perform(post("/api/appointments/" + appointmentId + "/cancel"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/appointments/" + appointmentId + "/cancel")
            .header("Authorization", "Bearer " + token("PATIENT")))
            .andExpect(status().isForbidden());
    }

    @Test
    void unknownAppointmentIsNotFound() throws Exception {
        String token = token("DOCTOR");
        UUID missing = UUID.randomUUID();

        mockMvc.perform(post("/api/appointments/" + missing + "/cancel")
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/appointments/" + missing + "/complete")
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/appointments/" + missing + "/reschedule")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(rescheduleBody(nextSlot())))
            .andExpect(status().isNotFound());
    }

    @Test
    void reschedulingToThePastIsBadRequestAndToAnOccupiedSlotIsConflict() throws Exception {
        String token = token("DOCTOR");
        Instant taken = nextSlot();
        scheduledAt(token, taken);
        String appointmentId = scheduled(token);

        mockMvc.perform(post("/api/appointments/" + appointmentId + "/reschedule")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(rescheduleBody(Instant.now().minusSeconds(60))))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/appointments/" + appointmentId + "/reschedule")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(rescheduleBody(taken)))
            .andExpect(status().isConflict());
    }

    @Test
    void cancellingTwiceIsConflictAndCompletingTwiceIsNot() throws Exception {
        String token = token("DOCTOR");
        String cancelled = scheduled(token);
        String completed = scheduled(token);

        mockMvc.perform(post("/api/appointments/" + cancelled + "/cancel")
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/appointments/" + cancelled + "/cancel")
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isConflict());

        mockMvc.perform(post("/api/appointments/" + completed + "/complete")
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/appointments/" + completed + "/complete")
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());
    }

    @Test
    void cancelledAppointmentCannotBeCompleted() throws Exception {
        String token = token("DOCTOR");
        String appointmentId = scheduled(token);

        mockMvc.perform(post("/api/appointments/" + appointmentId + "/cancel")
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/appointments/" + appointmentId + "/complete")
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isConflict());
    }

    private String scheduled(String token) throws Exception {
        return scheduledAt(token, nextSlot());
    }

    private String scheduledAt(String token, Instant slot) throws Exception {
        String response = mockMvc.perform(post("/api/appointments")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(scheduleBody(slot)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return response.replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }

    private static Instant nextSlot() {
        return Instant.now().plusSeconds(7200 + SLOT.incrementAndGet() * 60);
    }

    private static String scheduleBody(Instant slot) {
        return """
            {"patientId":"%s","doctorId":"%s","scheduledAt":"%s","fitIn":false,"fitInReason":null}
            """.formatted(PATIENT, DOCTOR, slot);
    }

    private static String rescheduleBody(Instant slot) {
        return """
            {"scheduledAt":"%s","fitIn":false,"fitInReason":null}
            """.formatted(slot);
    }

    private static String token(String role) throws JOSEException {
        return JwtTestSupport.issueToken(signingKey, UUID.randomUUID(), role);
    }
}
