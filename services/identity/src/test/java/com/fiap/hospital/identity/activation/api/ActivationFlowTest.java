package com.fiap.hospital.identity.activation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fiap.hospital.identity.accounts.domain.ActivationToken;
import com.fiap.hospital.identity.accounts.domain.ActivationTokenHash;
import com.fiap.hospital.identity.accounts.domain.Role;
import com.fiap.hospital.identity.accounts.domain.User;
import com.fiap.hospital.identity.accounts.repository.ActivationTokenRepository;
import com.fiap.hospital.identity.accounts.repository.UserRepository;
import com.fiap.hospital.identity.activation.service.ActivateAccount;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@Testcontainers
class ActivationFlowTest {

    private static final AtomicLong CPF_SEQUENCE = new AtomicLong(80_000_000_000L);
    private static final String PASSWORD = "s3nha-segura";
    private static final String TOKEN = "3Yb9Qk2Lm7Rx0Tn5";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ActivationTokenRepository activationTokenRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .build();
    }

    @Test
    void activateThenLoginIssuesJwtAndTokenCannotBeReused() throws Exception {
        UUID patientId = UUID.randomUUID();
        String taxIdentifier = nextCpf();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        userRepository.save(new User(
            patientId, taxIdentifier, "Ana Ribeiro", "ana.ribeiro@exemplo.com",
            Role.PATIENT, "PENDING_ACTIVATION", null
        ));
        activationTokenRepository.save(new ActivationToken(
            UUID.randomUUID(),
            patientId,
            ActivationTokenHash.of(TOKEN),
            now.plus(24, ChronoUnit.HOURS),
            now
        ));

        mockMvc.perform(post("/auth/login").with(httpBasic(taxIdentifier, PASSWORD)))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/auth/activate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(activateBody(TOKEN, PASSWORD)))
            .andExpect(status().isOk());

        User user = userRepository.findById(patientId).orElseThrow();
        assertThat(user.getStatus()).isEqualTo("ACTIVE");
        assertThat(user.getPasswordHash()).isNotBlank();
        assertThat(activationTokenRepository.findByTokenHash(ActivationTokenHash.of(TOKEN))
            .orElseThrow()
            .getConsumedAt()).isNotNull();

        mockMvc.perform(post("/auth/login").with(httpBasic(taxIdentifier, PASSWORD)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.tokenType").value("Bearer"));

        mockMvc.perform(post("/auth/activate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(activateBody(TOKEN, PASSWORD)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value(ActivateAccount.INVALID_TOKEN));
    }

    @Test
    void expiredAndUnknownTokensReturnTheSameError() throws Exception {
        UUID patientId = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        userRepository.save(new User(
            patientId, nextCpf(), "Ana Ribeiro", "ana.ribeiro@exemplo.com",
            Role.PATIENT, "PENDING_ACTIVATION", null
        ));
        activationTokenRepository.save(new ActivationToken(
            UUID.randomUUID(),
            patientId,
            ActivationTokenHash.of("token-expirado"),
            now.minus(1, ChronoUnit.MINUTES),
            now.minus(2, ChronoUnit.HOURS)
        ));

        var expired = mockMvc.perform(post("/auth/activate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(activateBody("token-expirado", PASSWORD)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value(ActivateAccount.INVALID_TOKEN))
            .andReturn()
            .getResponse()
            .getContentAsString();

        var unknown = mockMvc.perform(post("/auth/activate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(activateBody("token-inexistente", PASSWORD)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value(ActivateAccount.INVALID_TOKEN))
            .andReturn()
            .getResponse()
            .getContentAsString();

        assertThat(expired).isEqualTo(unknown);
        assertThat(userRepository.findById(patientId).orElseThrow().getStatus())
            .isEqualTo("PENDING_ACTIVATION");
    }

    private static String activateBody(String token, String password) {
        return """
            {"token":"%s","password":"%s"}
            """.formatted(token, password);
    }

    private static String nextCpf() {
        return String.valueOf(CPF_SEQUENCE.incrementAndGet());
    }
}
