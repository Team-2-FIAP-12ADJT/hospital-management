package com.fiap.hospital.scheduling.participants.api;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// Dois aceites do card 25 sem cobertura de prova: "anônimo recebe 401" e "papel
// indevido recebe 403". Aqui se exercitam as duas rotas protegidas do
// DoctorController via MockMvc, com o contexto completo (igual ao
// OutboxEventWriterTest) e o JWT simulado pelo spring-security-test.
//
// Spring Boot 4.1 removeu @MockBean/@AutoConfigureMockMvc dos pacotes usuais, então
// o MockMvc é montado manualmente a partir do WebApplicationContext e o
// DoctorRegistrationService é o bean real (não é tocado nos cenários 401/403).
@SpringBootTest
@Testcontainers
class DoctorControllerSecurityTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    private static final String VALID_BODY = """
        {
          "taxIdentifier": "98765432100",
          "crm": "CRM-SP 654321",
          "specialty": "Neurologia",
          "name": "Dr. Joao Mendes",
          "email": "joao.mendes@hospital.local"
        }
        """;

    @Test
    void anonymous_receives_401() throws Exception {
        mockMvc.perform(post("/api/doctors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrong_role_receives_403() throws Exception {
        mockMvc.perform(post("/api/doctors")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PATIENT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());
    }
}
