package com.fiap.hospital.notification.notifications.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fiap.hospital.notification.config.FastBackOffTestConfig;
import com.fiap.hospital.notification.notifications.service.AppointmentNotifications;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// A config de teste do modulo desliga auto-startup do listener e auto-create do
// KafkaAdmin por padrao (application.yml de teste); aqui precisamos dos dois
// ligados para o listener real consumir e para o NewTopic da DLT nascer no boot.
@SpringBootTest(properties = {
    "spring.kafka.consumer.auto-offset-reset=earliest",
    "spring.kafka.listener.auto-startup=true",
    "spring.kafka.admin.auto-create=true"
})
// A DLT NAO entra aqui de proposito: ela tem de nascer do NewTopic da aplicacao.
// Com auto-create do broker desligado, um destino errado no resolver falha o
// teste em vez de ser criado por baixo do pano.
@EmbeddedKafka(
    partitions = 1,
    topics = {"hospital.appointment"},
    brokerProperties = {"auto.create.topics.enable=false"}
)
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
@Import(FastBackOffTestConfig.class)
class AppointmentEventConsumerRecoveryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @MockitoSpyBean
    private AppointmentEventParser parser;

    @MockitoSpyBean
    private AppointmentNotifications appointmentNotifications;

    @Test
    void unparseableMessageIsRoutedToDltAndDoesNotLogPayload(CapturedOutput output) throws Exception {
        kafkaTemplate.send(
            "hospital.appointment", "invalid-key", "{\"invalid\":\"appointment-secret-payload\"}"
        ).get(10, TimeUnit.SECONDS);

        long mine = waitForDlt(
            "hospital.appointment.DLT",
            "{\"invalid\":\"appointment-secret-payload\"}"
        );
        assertThat(mine).isEqualTo(1);

        Thread.sleep(1000);
        assertThat(output.getOut()).doesNotContain("appointment-secret-payload");

        // Malformado e determinista: uma entrega, sem retry.
        Mockito.verify(parser, Mockito.times(1))
            .parse("{\"invalid\":\"appointment-secret-payload\"}");
    }

    // Entrega eventual na DLT nao prova a classificacao: um malformado reentregue
    // varias vezes tambem chega la. O que separa os dois casos e a contagem.
    @Test
    void malformedIsDeliveredOnceWhileTransientFailureIsRetriedThroughTheFullBackoff() throws Exception {
        Mockito.doThrow(new IllegalStateException("banco indisponivel"))
            .when(appointmentNotifications).schedule(Mockito.any());

        String malformed = "nao-e-json-appointment";
        String valid = validScheduledEnvelope();

        kafkaTemplate.send("hospital.appointment", "k1", malformed).get(10, TimeUnit.SECONDS);
        kafkaTemplate.send("hospital.appointment", "k2", valid).get(10, TimeUnit.SECONDS);

        Set<String> awaited = new HashSet<>(Set.of(malformed, valid));
        drainDltUntilEmpty("hospital.appointment.DLT", awaited);
        assertThat(awaited).isEmpty();

        // Malformado e determinista: uma entrega, sem retry.
        Mockito.verify(parser, Mockito.times(1)).parse(malformed);
        // Transitorio percorre o backoff limitado: entrega inicial mais 5 tentativas
        // (ExponentialBackOffWithMaxRetries(5) do KafkaErrorHandlingConfig).
        Mockito.verify(appointmentNotifications, Mockito.times(6)).schedule(Mockito.any());
    }

    private long waitForDlt(String topic, String expectedPayload) throws Exception {
        Map<String, Object> consumerProps =
            KafkaTestUtils.consumerProps("dlt-group-" + UUID.randomUUID(), "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
            consumerProps, new StringDeserializer(), new StringDeserializer()).createConsumer()
        ) {
            consumer.subscribe(List.of(topic));
            long mine = 0;
            Instant deadline = Instant.now().plusSeconds(20);
            while (mine == 0 && Instant.now().isBefore(deadline)) {
                mine = StreamSupport.stream(
                        KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5)).spliterator(), false)
                    .filter(record -> expectedPayload.equals(record.value()))
                    .count();
            }
            return mine;
        }
    }

    private void drainDltUntilEmpty(String topic, Set<String> awaited) {
        Map<String, Object> consumerProps =
            KafkaTestUtils.consumerProps("dlt-count-group-" + UUID.randomUUID(), "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
            consumerProps, new StringDeserializer(), new StringDeserializer()).createConsumer()
        ) {
            consumer.subscribe(List.of(topic));
            Instant deadline = Instant.now().plusSeconds(60);
            while (!awaited.isEmpty() && Instant.now().isBefore(deadline)) {
                KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5))
                    .forEach(record -> awaited.remove(record.value()));
            }
        }
    }

    private static String validScheduledEnvelope() {
        return EventFixtures.appointmentScheduled(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }
}
