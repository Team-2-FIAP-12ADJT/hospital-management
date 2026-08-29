package com.fiap.hospital.scheduling.participants.api;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fiap.hospital.scheduling.support.JwtTestSupport;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
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

// Cobre os dois aceites do card 25 que estavam sem prova — "anônimo recebe 401" e
// "papel indevido recebe 403" — mais 400, 409 e o 201 do caminho feliz.
//
// O token é assinado no próprio teste e validado pelo decoder e pelo
// JwtAuthenticationConverter reais: `jwt()` do spring-security-test injeta a autoridade
// direto e nunca executa o conversor, então não prova que o claim `role` vira ROLE_<X>.
//
// Spring Boot 4.1 removeu @MockBean/@AutoConfigureMockMvc dos pacotes usuais, então o
// MockMvc é montado manualmente a partir do WebApplicationContext e o
// DoctorRegistrationService é o bean real.
@SpringBootTest
@Testcontainers
class DoctorControllerSecurityTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
        "postgres:18-alpine"
    );

    private static final AtomicLong UNIQUE_SEQUENCE = new AtomicLong(
        1_000_000_000L
    );

    private static final String VALID_BODY = """
    {
      "taxIdentifier": "98765432100",
      "crm": "CRM-SP 654321",
      "specialty": "Neurologia",
      "name": "Dr. Joao Mendes",
      "email": "joao.mendes@hospital.local"
    }
    """;

    private static final String INVALID_BODY = """
    {
      "taxIdentifier": "1234567890",
      "crm": "CRM-SP 12345678901234",
      "specialty": "Neurologia",
      "name": "Dr. Joao Mendes",
      "email": "email-invalido"
    }
    """;

    private static String validBody(String taxIdentifier, String crm) {
        return """
        {
          "taxIdentifier": "%s",
          "crm": "%s",
          "specialty": "Neurologia",
          "name": "Dr. Joao Mendes",
          "email": "joao.mendes@hospital.local"
        }
        """.formatted(taxIdentifier, crm);
    }

    private static String uniqueTaxIdentifier() {
        long next = UNIQUE_SEQUENCE.getAndIncrement();
        return String.format("%011d", next);
    }

    private static String uniqueCrm() {
        return (
            "CRM-SP " +
            String.format(
                "%06d",
                UNIQUE_SEQUENCE.getAndIncrement() % 1_000_000L
            )
        );
    }

    private static HttpServer jwkServer;
    private static RSAKey signingKey;

    // O JWKS é servido localmente para que o token seja validado pelo decoder real,
    // com o issuer, o audience e o algoritmo que vêm do application.yml de teste.
    @DynamicPropertySource
    static void registerJwksUri(DynamicPropertyRegistry registry)
        throws JOSEException, IOException {
        if (jwkServer == null) {
            signingKey = JwtTestSupport.newSigningKey(
                "doctor-security-test-key"
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
    void anonymous_receives_401() throws Exception {
        mockMvc
            .perform(
                post("/api/doctors")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_BODY)
            )
            .andExpect(status().isUnauthorized());
    }

    @Test
    void wrong_role_receives_403() throws Exception {
        String token = issueToken(UUID.randomUUID(), "PATIENT");

        mockMvc
            .perform(
                post("/api/doctors")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_BODY)
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void invalid_payload_receives_400() throws Exception {
        String token = issueToken(UUID.randomUUID(), "DOCTOR");

        mockMvc
            .perform(
                post("/api/doctors")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(INVALID_BODY)
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    void doctor_registration_with_real_bearer_token_receives_201()
        throws Exception {
        String token = issueToken(UUID.randomUUID(), "DOCTOR");
        String taxIdentifier = uniqueTaxIdentifier();
        String crm = uniqueCrm();

        mockMvc
            .perform(
                post("/api/doctors")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validBody(taxIdentifier, crm))
            )
            .andExpect(status().isCreated());
    }

    @Test
    void duplicate_tax_identifier_receives_409() throws Exception {
        String firstToken = issueToken(UUID.randomUUID(), "DOCTOR");
        String secondToken = issueToken(UUID.randomUUID(), "DOCTOR");
        String firstTaxIdentifier = uniqueTaxIdentifier();
        String firstCrm = uniqueCrm();
        String secondCrm = uniqueCrm();

        mockMvc
            .perform(
                post("/api/doctors")
                    .header("Authorization", "Bearer " + firstToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validBody(firstTaxIdentifier, firstCrm))
            )
            .andExpect(status().isCreated());

        mockMvc
            .perform(
                post("/api/doctors")
                    .header("Authorization", "Bearer " + secondToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validBody(firstTaxIdentifier, secondCrm))
            )
            .andExpect(status().isConflict());
    }

    private static String issueToken(UUID subject, String role)
        throws JOSEException {
        return JwtTestSupport.issueToken(signingKey, subject, role);
    }
}
