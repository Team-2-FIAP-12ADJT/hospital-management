package com.fiap.hospital.scheduling.participants.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fiap.hospital.scheduling.participants.domain.Doctor;
import com.fiap.hospital.scheduling.participants.repository.DoctorRepository;
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
class DoctorRegistrationServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
        "postgres:18-alpine"
    );

    private static final AtomicLong CPF_SEQUENCE = new AtomicLong(
        10_000_000_000L + (System.nanoTime() % 80_000_000_000L)
    );

    @Autowired
    private DoctorRegistrationService service;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private JsonMapper mapper;

    private static String nextCpf() {
        return String.valueOf(CPF_SEQUENCE.incrementAndGet());
    }

    @Test
    void register_persists_doctor_and_outbox_event_with_expected_contract()
        throws Exception {
        String taxIdentifier = nextCpf();
        String crm = "CRM-SP " + nextCpf();

        Doctor doctor = service.register(
            taxIdentifier,
            crm,
            "Cardiologia",
            "Dr. Joao Mendes",
            "joao.mendes@hospital.local"
        );

        assertThat(doctorRepository.findById(doctor.getId())).isPresent();
        assertThat(
            jdbcClient
                .sql("SELECT count(*) FROM participants.doctor WHERE id = :id")
                .param("id", doctor.getId())
                .query(Long.class)
                .single()
        ).isEqualTo(1L);

        String envelope = jdbcClient
            .sql(
                "SELECT envelope FROM public.outbox_events WHERE aggregate_id = :id"
            )
            .param("id", doctor.getId())
            .query(String.class)
            .single();

        JsonNode root = mapper.readTree(envelope);
        assertThat(root.size()).isEqualTo(5);
        assertThat(root.get("eventType").asString()).isEqualTo(
            "DoctorRegistered"
        );
        assertThat(root.get("data").size()).isEqualTo(7);

        Set<String> dataFields = new HashSet<>();
        for (Map.Entry<String, JsonNode> entry : root
            .get("data")
            .properties()) {
            dataFields.add(entry.getKey());
        }
        assertThat(dataFields).containsExactlyInAnyOrder(
            "doctorId",
            "taxIdentifier",
            "crm",
            "specialty",
            "name",
            "email",
            "role"
        );

        assertThat(
            jdbcClient
                .sql(
                    "SELECT aggregate_type, type, topic FROM public.outbox_events WHERE aggregate_id = :id"
                )
                .param("id", doctor.getId())
                .query(
                    (rs, rowNum) ->
                        rs.getString("aggregate_type") +
                        "|" +
                        rs.getString("type") +
                        "|" +
                        rs.getString("topic")
                )
                .single()
        ).isEqualTo("person|DoctorRegistered|hospital.person");

        JsonNode data = root.get("data");
        assertThat(data.get("doctorId").asString()).isEqualTo(
            doctor.getId().toString()
        );
        assertThat(data.get("taxIdentifier").asString()).isEqualTo(
            taxIdentifier
        );
        assertThat(data.get("crm").asString()).isEqualTo(crm);
        assertThat(data.get("specialty").asString()).isEqualTo("Cardiologia");
        assertThat(data.get("name").asString()).isEqualTo("Dr. Joao Mendes");
        assertThat(data.get("email").asString()).isEqualTo(
            "joao.mendes@hospital.local"
        );
        assertThat(data.get("role").asString()).isEqualTo("DOCTOR");
    }

    @Test
    void register_rejects_duplicate_tax_identifier_and_duplicate_crm_with_409() {
        String taxIdentifier = nextCpf();
        String crm = "CRM-SP " + nextCpf();

        service.register(
            taxIdentifier,
            crm,
            "Cardiologia",
            "Dr. First",
            "dr.first@hospital.local"
        );

        assertThatThrownBy(() ->
            service.register(
                taxIdentifier,
                "CRM-SP " + nextCpf(),
                "Cardiologia",
                "Dr. Not Allowed",
                "dr.other@hospital.local"
            )
        )
            .isInstanceOf(ResponseStatusException.class)
            .extracting("statusCode")
            .isEqualTo(HttpStatus.CONFLICT);

        assertThatThrownBy(() ->
            service.register(
                nextCpf(),
                crm,
                "Cardiologia",
                "Dr. Not Allowed",
                "dr.other@hospital.local"
            )
        )
            .isInstanceOf(ResponseStatusException.class)
            .extracting("statusCode")
            .isEqualTo(HttpStatus.CONFLICT);
    }
}
