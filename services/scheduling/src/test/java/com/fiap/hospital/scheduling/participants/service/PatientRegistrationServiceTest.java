package com.fiap.hospital.scheduling.participants.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fiap.hospital.scheduling.participants.domain.Patient;
import com.fiap.hospital.scheduling.participants.repository.PatientRepository;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@Testcontainers
class PatientRegistrationServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
        "postgres:18-alpine"
    );

    private static final AtomicLong CPF_SEQUENCE = new AtomicLong(
        10_000_000_000L + (System.nanoTime() % 80_000_000_000L)
    );

    @Autowired
    private PatientRegistrationService service;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private JsonMapper mapper;

    private static String nextCpf() {
        return String.valueOf(CPF_SEQUENCE.incrementAndGet());
    }

    @Test
    void register_persists_patient_and_outbox_event_with_expected_contract()
        throws Exception {
        String taxIdentifier = nextCpf();
        String phone = "+5511999999999";

        Patient patient = service.register(
            taxIdentifier,
            "Maria Souza",
            "maria.souza@hospital.local",
            phone
        );

        assertThat(patientRepository.findById(patient.getId())).isPresent();
        assertThat(
            jdbcClient
                .sql("SELECT count(*) FROM participants.patient WHERE id = :id")
                .param("id", patient.getId())
                .query(Long.class)
                .single()
        ).isEqualTo(1L);

        String envelope = jdbcClient
            .sql(
                "SELECT envelope FROM public.outbox_events WHERE aggregate_id = :id"
            )
            .param("id", patient.getId())
            .query(String.class)
            .single();

        JsonNode root = mapper.readTree(envelope);
        assertThat(root.size()).isEqualTo(5);
        assertThat(root.get("eventType").asString()).isEqualTo(
            "PatientRegistered"
        );
        assertThat(root.get("eventVersion").asInt()).isEqualTo(1);
        assertThat(root.get("data").size()).isEqualTo(6);

        Set<String> dataFields = new HashSet<>();
        for (Map.Entry<String, JsonNode> entry : root
            .get("data")
            .properties()) {
            dataFields.add(entry.getKey());
        }
        assertThat(dataFields).containsExactlyInAnyOrder(
            "patientId",
            "taxIdentifier",
            "name",
            "email",
            "phone",
            "role"
        );

        assertThat(
            jdbcClient
                .sql(
                    "SELECT aggregate_type, type, topic FROM public.outbox_events WHERE aggregate_id = :id"
                )
                .param("id", patient.getId())
                .query(
                    (rs, rowNum) ->
                        rs.getString("aggregate_type") +
                        "|" +
                        rs.getString("type") +
                        "|" +
                        rs.getString("topic")
                )
                .single()
        ).isEqualTo("person|PatientRegistered|hospital.person");

        JsonNode data = root.get("data");
        assertThat(data.get("patientId").asString()).isEqualTo(
            patient.getId().toString()
        );
        assertThat(data.get("taxIdentifier").asString()).isEqualTo(
            taxIdentifier
        );
        assertThat(data.get("name").asString()).isEqualTo("Maria Souza");
        assertThat(data.get("email").asString()).isEqualTo(
            "maria.souza@hospital.local"
        );
        assertThat(data.get("phone").asString()).isEqualTo(phone);
        assertThat(data.get("role").asString()).isEqualTo("PATIENT");
    }

    @Test
    void register_rejects_duplicate_tax_identifier_with_409() {
        String taxIdentifier = nextCpf();

        service.register(
            taxIdentifier,
            "Maria Souza",
            "maria.souza@hospital.local",
            "+5511999999999"
        );

        assertThatThrownBy(() ->
            service.register(
                taxIdentifier,
                "Maria Souza Outra",
                "maria.outra@hospital.local",
                "+5511999999998"
            )
        )
            .isInstanceOf(ResponseStatusException.class)
            .extracting("statusCode")
            .isEqualTo(HttpStatus.CONFLICT);
    }
}
