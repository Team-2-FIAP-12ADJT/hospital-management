package com.fiap.hospital.scheduling.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@Testcontainers
class OutboxEventWriterTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    private static final AtomicLong CPF_SEQUENCE =
        new AtomicLong(10_000_000_000L + (System.nanoTime() % 80_000_000_000L));

    @Autowired
    private OutboxEventWriter writer;

    @Autowired
    private OutboxEventRepository repository;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JsonMapper mapper;

    @Autowired
    private EntityManager entityManager;

    private static String nextCpf() {
        return String.valueOf(CPF_SEQUENCE.incrementAndGet());
    }

    private void insertPatient(UUID id) {
        jdbcClient.sql(
                "INSERT INTO participants.patient (id, tax_identifier, name, email) VALUES (:id, :taxId, :name, :email)")
            .param("id", id)
            .param("taxId", nextCpf())
            .param("name", "Paciente de Teste")
            .param("email", "paciente@teste.local")
            .update();
    }

    private long countPatient(UUID id) {
        return jdbcClient.sql("SELECT count(*) FROM participants.patient WHERE id = :id")
            .param("id", id)
            .query(Long.class)
            .single();
    }

    private long countOutboxEvent(UUID id) {
        return jdbcClient.sql("SELECT count(*) FROM public.outbox_events WHERE id = :id")
            .param("id", id)
            .query(Long.class)
            .single();
    }

    @Test
    void gravaAgregadoEEventoNaMesmaTransacao() {
        UUID patientId = UUID.randomUUID();
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        UUID eventId = transactionTemplate.execute(status -> {
            insertPatient(patientId);
            return writer.append(Aggregate.PERSON, patientId, "PatientRegistered", 1,
                Instant.parse("2026-08-17T14:05:03.123456789Z"),
                new PatientRegisteredData(patientId, "Paciente de Teste"));
        });

        assertThat(countPatient(patientId)).isEqualTo(1);
        assertThat(repository.findById(eventId)).isPresent();
    }

    @Test
    void rollbackDaTransacaoDerrubaAgregadoEEvento() {
        UUID patientId = UUID.randomUUID();
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        UUID[] eventIdHolder = new UUID[1];
        RuntimeException failure = new RuntimeException("falha proposital antes do commit");

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> transactionTemplate.execute(status -> {
            insertPatient(patientId);
            eventIdHolder[0] = writer.append(Aggregate.PERSON, patientId, "PatientRegistered", 1,
                Instant.parse("2026-08-17T14:05:03.123456789Z"),
                new PatientRegisteredData(patientId, "Paciente de Teste"));

            entityManager.flush();
            assertThat(countOutboxEvent(eventIdHolder[0]))
                .as("outbox visível dentro da transação antes do rollback")
                .isEqualTo(1);

            throw failure;
        }));

        assertThat(thrown).isSameAs(failure);
        assertThat(countPatient(patientId)).isZero();
        assertThat(countOutboxEvent(eventIdHolder[0])).isZero();
        assertThat(repository.findById(eventIdHolder[0])).isEmpty();
    }

    @Test
    void chamarForaDeTransacaoFalhaAlto() {
        assertThrows(IllegalTransactionStateException.class, () -> writer.append(Aggregate.PERSON,
            UUID.randomUUID(), "PatientRegistered", 1, Instant.now(),
            new PatientRegisteredData(UUID.randomUUID(), "Paciente de Teste")));
    }

    @Test
    void envelopeGravadoTemAsQuatroChavesMaisDataEOccurredAtComoTextoIso() {
        UUID patientId = UUID.randomUUID();
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        PatientRegisteredData data = new PatientRegisteredData(patientId, "Paciente de Teste");
        Instant occurredAt = Instant.parse("2026-08-17T14:05:03.123456789Z");

        UUID eventId = transactionTemplate.execute(status -> {
            insertPatient(patientId);
            return writer.append(Aggregate.PERSON, patientId, "PatientRegistered", 1, occurredAt, data);
        });

        OutboxEvent event = repository.findById(eventId).orElseThrow();
        JsonNode root = mapper.readTree(event.envelope());

        assertThat(root.has("eventId")).isTrue();
        assertThat(root.has("eventType")).isTrue();
        assertThat(root.has("eventVersion")).isTrue();
        assertThat(root.has("occurredAt")).isTrue();
        assertThat(root.has("data")).isTrue();

        assertThat(event.id()).isEqualTo(eventId);
        assertThat(root.get("eventId").asString()).isEqualTo(eventId.toString());

        assertThat(event.type()).isEqualTo(root.get("eventType").asString());
        assertThat(event.eventVersion()).isEqualTo(root.get("eventVersion").asInt());
        assertThat(event.occurredAt()).isEqualTo(Instant.parse(root.get("occurredAt").asString()));

        assertThat(root.get("eventType").asString()).isEqualTo("PatientRegistered");
        assertThat(root.get("data").get("patientId").asString()).isEqualTo(patientId.toString());
        assertThat(root.get("data").get("name").asString()).isEqualTo("Paciente de Teste");

        JsonNode occurredAtNode = root.get("occurredAt");
        assertThat(occurredAtNode.isString()).isTrue();
        assertThat(occurredAtNode.asString()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z");
    }

    @Test
    void occurredAtDeSegundoExatoMantemAsTresCasasDecimais() {
        UUID patientId = UUID.randomUUID();
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        PatientRegisteredData data = new PatientRegisteredData(patientId, "Paciente de Teste");
        Instant occurredAt = Instant.parse("2026-08-17T14:05:03Z");

        UUID eventId = transactionTemplate.execute(status -> {
            insertPatient(patientId);
            return writer.append(Aggregate.PERSON, patientId, "PatientRegistered", 1, occurredAt, data);
        });

        OutboxEvent event = repository.findById(eventId).orElseThrow();
        JsonNode root = mapper.readTree(event.envelope());

        assertThat(root.get("occurredAt").asString()).isEqualTo("2026-08-17T14:05:03.000Z");
    }

    record PatientRegisteredData(UUID patientId, String name) {}
}
