package com.fiap.hospital.identity.accounts.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fiap.hospital.identity.accounts.domain.Role;
import com.fiap.hospital.identity.accounts.domain.User;
import com.fiap.hospital.identity.accounts.idempotency.ProcessedEventRepository;
import com.fiap.hospital.identity.accounts.repository.UserRepository;
import com.fiap.hospital.identity.accounts.service.AccountPrincipal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@Testcontainers
class ProvisionAccountFromPersonEventTest {

    private static final UUID SEEDED_PATIENT_ID =
        UUID.fromString("00000000-0000-4000-8000-000000000003");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    private static final AtomicLong CPF_SEQUENCE = new AtomicLong(60_000_000_000L);

    @Autowired
    private PersonEventConsumer consumer;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    private static String nextCpf() {
        return String.valueOf(CPF_SEQUENCE.incrementAndGet());
    }

    @Test
    void provisionaPacienteComOMesmoIdDoEventoInativoESemSenha() {
        UUID eventId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        String taxIdentifier = nextCpf();

        consumer.consume(PersonEventFixtures.patientRegistered(eventId, patientId, taxIdentifier));

        User user = userRepository.findById(patientId).orElseThrow();
        assertThat(user.getId()).isEqualTo(patientId);
        assertThat(user.getTaxIdentifier()).isEqualTo(taxIdentifier);
        assertThat(user.getName()).isEqualTo("Ana Ribeiro");
        assertThat(user.getEmail()).isEqualTo("ana.ribeiro@exemplo.com");
        assertThat(user.getRole()).isEqualTo(Role.PATIENT);
        assertThat(user.getStatus()).isEqualTo("PENDING_ACTIVATION");
        assertThat(user.getPasswordHash()).isNull();
        assertThat(new AccountPrincipal(user).isEnabled()).isFalse();
        assertThat(processedEventRepository.existsById(eventId)).isTrue();
    }

    @Test
    void provisionaMedicoComOMesmoIdEPapelDoEvento() {
        UUID eventId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        String taxIdentifier = nextCpf();

        consumer.consume(PersonEventFixtures.doctorRegistered(eventId, doctorId, taxIdentifier));

        User user = userRepository.findById(doctorId).orElseThrow();
        assertThat(user.getId()).isEqualTo(doctorId);
        assertThat(user.getTaxIdentifier()).isEqualTo(taxIdentifier);
        assertThat(user.getRole()).isEqualTo(Role.DOCTOR);
        assertThat(user.getStatus()).isEqualTo("PENDING_ACTIVATION");
        assertThat(user.getPasswordHash()).isNull();
        assertThat(new AccountPrincipal(user).isEnabled()).isFalse();
    }

    @Test
    void eventoRepetidoNaoCriaSegundaConta() {
        UUID eventId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        String taxIdentifier = nextCpf();
        String envelope = PersonEventFixtures.patientRegistered(eventId, patientId, taxIdentifier);
        long usersBefore = userRepository.count();

        consumer.consume(envelope);
        consumer.consume(envelope);

        assertThat(userRepository.count()).isEqualTo(usersBefore + 1);
        assertThat(userRepository.findById(patientId)).isPresent();
        assertThat(processedEventRepository.existsById(eventId)).isTrue();
    }

    @Test
    void ignoraPatientContactUpdatedSemGravarConta() {
        UUID eventId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        long usersBefore = userRepository.count();

        consumer.consume(PersonEventFixtures.contactUpdated(eventId, patientId));

        assertThat(userRepository.count()).isEqualTo(usersBefore);
        assertThat(userRepository.findById(patientId)).isEmpty();
        assertThat(processedEventRepository.existsById(eventId)).isFalse();
    }

    @Test
    void naoSobrescreveContaSemeadaQuandoOIdJaExiste() {
        UUID eventId = UUID.randomUUID();
        User before = userRepository.findById(SEEDED_PATIENT_ID).orElseThrow();

        consumer.consume(PersonEventFixtures.patientRegistered(eventId, SEEDED_PATIENT_ID, nextCpf()));

        User after = userRepository.findById(SEEDED_PATIENT_ID).orElseThrow();
        assertThat(after.getName()).isEqualTo(before.getName());
        assertThat(after.getStatus()).isEqualTo("ACTIVE");
        assertThat(after.getPasswordHash()).isEqualTo(before.getPasswordHash());
        assertThat(processedEventRepository.existsById(eventId)).isTrue();
    }
}
