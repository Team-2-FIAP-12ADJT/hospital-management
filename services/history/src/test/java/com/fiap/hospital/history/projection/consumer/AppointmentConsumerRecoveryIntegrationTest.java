package com.fiap.hospital.history.projection.consumer;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.mockito.Mockito;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.stream.StreamSupport;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.kafka.consumer.auto-offset-reset=earliest"
})
// A DLT NAO entra aqui de proposito: ela tem de nascer do NewTopic da aplicacao.
// Com auto-create desligado, um destino errado no resolver falha o teste em vez
// de ser criado por baixo do pano.
@EmbeddedKafka(
    partitions = 1,
    topics = {"hospital.appointment"},
    brokerProperties = {"auto.create.topics.enable=false"}
)
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class AppointmentConsumerRecoveryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @MockitoSpyBean
    private AppointmentEventParser parser;

    @MockitoSpyBean
    private ApplyAppointmentEvent applyEvent;

    @Test
    void unparseableMessageIsRoutedToDltAndDoesNotLogPayload(CapturedOutput output) throws Exception {
        // Send a malformed message
        kafkaTemplate.send("hospital.appointment", "invalid-key", "{\"invalid\":\"json-secret-payload\"}").get(10, TimeUnit.SECONDS);

        // Consume from DLT to verify
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("dlt-group", "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(consumerProps, new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            // consumeFromAnEmbeddedTopic so aceita topico da lista do @EmbeddedKafka, e a
            // DLT nasce do NewTopic da aplicacao de proposito: assinar direto e o que
            // mantem o teste sensivel a um destino errado no resolver.
            consumer.subscribe(List.of("hospital.appointment.DLT"));
            // A DLT e compartilhada pelos testes da classe. Parar o poll no primeiro
            // registro e filtrar depois torna o resultado dependente de ordem: se o
            // registro do outro teste chegar antes, este nao acha o seu. Espera pelo
            // proprio payload ate o prazo.
            long mine = 0;
            Instant deadline = Instant.now().plusSeconds(20);
            while (mine == 0 && Instant.now().isBefore(deadline)) {
                mine = StreamSupport.stream(
                                KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5)).spliterator(), false)
                        .filter(record -> "{\"invalid\":\"json-secret-payload\"}".equals(record.value()))
                        .count();
            }

            assertThat(mine).isEqualTo(1);
        }

        // Wait a bit to ensure logs are flushed
        Thread.sleep(1000);
        assertThat(output.getOut()).doesNotContain("json-secret-payload");
    }

    // Entrega eventual na DLT nao prova a classificacao: um malformado reentregue
    // tres vezes tambem chega la. O que separa os dois casos e a contagem.
    @Test
    void malformedIsDeliveredOnceWhileTransientFailureIsRetriedWithinTheBound() throws Exception {
        Mockito.doThrow(new IllegalStateException("banco indisponivel"))
                .when(applyEvent).apply(Mockito.any());

        kafkaTemplate.send("hospital.appointment", "k1", "nao-e-json").get(10, TimeUnit.SECONDS);
        kafkaTemplate.send("hospital.appointment", "k2", validScheduledEnvelope()).get(10, TimeUnit.SECONDS);

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("dlt-count-group", "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // A DLT e compartilhada pela classe: contar registros sem filtrar deixaria o
        // poll parar com os do outro teste e verificar antes de o retry terminar.
        // Espera pelos dois payloads deste teste, e so entao conta as tentativas.
        Set<String> awaited = new HashSet<>(Set.of("nao-e-json", validScheduledEnvelope()));
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(consumerProps, new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            consumer.subscribe(List.of("hospital.appointment.DLT"));
            Instant deadline = Instant.now().plusSeconds(30);
            while (!awaited.isEmpty() && Instant.now().isBefore(deadline)) {
                KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5))
                        .forEach(record -> awaited.remove(record.value()));
            }
        }

        // Os dois registros deste teste chegaram a DLT, cada um pelo seu caminho.
        assertThat(awaited).isEmpty();
        // Malformado e determinista: uma entrega, sem retry.
        Mockito.verify(parser, Mockito.times(1)).parse("nao-e-json");
        // Transitorio percorre o backoff limitado: entrega inicial mais duas tentativas.
        Mockito.verify(applyEvent, Mockito.times(3)).apply(Mockito.any());
    }

    private static String validScheduledEnvelope() {
        return """
                {
                  "eventId": "9f1c2d3e-4a5b-4c6d-8e7f-0a1b2c3d4e5f",
                  "eventType": "AppointmentScheduled",
                  "eventVersion": 1,
                  "occurredAt": "2026-09-02T13:30:00.000Z",
                  "data": {
                    "appointmentId": "7c1e5a93-2f84-4b60-8d17-a3e9c0524b6f",
                    "patientId": "3f2b8c10-5d47-4e91-9a2e-7c6f1b0d8e33",
                    "doctorId": "b91c4d72-8a05-4f36-b1de-0e5a72c4f118",
                    "scheduledAt": "2026-09-02T13:30:00.000Z",
                    "status": "SCHEDULED",
                    "fitIn": false,
                    "fitInReason": null,
                    "patientName": "Ana Ribeiro",
                    "doctorName": "Dr. Paulo Menezes",
                    "doctorSpecialty": "Cardiologia"
                  }
                }
                """;
    }
}
