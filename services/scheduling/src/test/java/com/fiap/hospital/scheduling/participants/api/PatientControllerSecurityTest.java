package com.fiap.hospital.scheduling.participants.api;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

@SpringBootTest
@Testcontainers
class PatientControllerSecurityTest {

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
      "name": "Maria Souza",
      "email": "maria.souza@hospital.local",
      "phone": "+5511999999999"
    }
    """;

    private static final String INVALID_BODY = """
    {
      "taxIdentifier": "1234567890",
      "name": "Maria Souza",
      "email": "email-invalido",
      "phone": "+55119999999999999999999999"
    }
    """;

    private static String validBody(String taxIdentifier) {
        return """
        {
          "taxIdentifier": "%s",
          "name": "Maria Souza",
          "email": "maria.souza@hospital.local",
          "phone": "+5511999999999"
        }
        """.formatted(taxIdentifier);
    }

    private static String uniqueTaxIdentifier() {
        long next = UNIQUE_SEQUENCE.getAndIncrement();
        return String.format("%011d", next);
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
    void anonymous_receives_201() throws Exception {
        mockMvc
            .perform(
                post("/api/patients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_BODY)
            )
            .andExpect(status().isCreated());
    }

    @Test
    void invalid_payload_receives_400() throws Exception {
        mockMvc
            .perform(
                post("/api/patients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(INVALID_BODY)
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    void anonymous_get_on_patient_collection_is_blocked() throws Exception {
        mockMvc
            .perform(get("/api/patients"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymous_post_on_patient_detail_route_is_blocked() throws Exception {
        mockMvc
            .perform(
                post("/api/patients/123e4567-e89b-12d3-a456-426614174000")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_BODY)
            )
            .andExpect(status().isUnauthorized());
    }

    @Test
    void duplicate_tax_identifier_receives_409() throws Exception {
        String firstTaxIdentifier = uniqueTaxIdentifier();
        String secondTaxIdentifier = uniqueTaxIdentifier();

        mockMvc
            .perform(
                post("/api/patients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validBody(firstTaxIdentifier))
            )
            .andExpect(status().isCreated());

        mockMvc
            .perform(
                post("/api/patients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validBody(firstTaxIdentifier))
            )
            .andExpect(status().isConflict());

        mockMvc
            .perform(
                post("/api/patients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validBody(secondTaxIdentifier))
            )
            .andExpect(status().isCreated());
    }
}
