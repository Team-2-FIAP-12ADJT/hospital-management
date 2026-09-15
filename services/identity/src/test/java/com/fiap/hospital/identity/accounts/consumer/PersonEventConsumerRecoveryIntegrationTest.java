package com.fiap.hospital.identity.accounts.consumer;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// A config de teste do modulo desliga o auto-startup do listener por padrao
// (ProvisioningAtomicityIntegrationTest chama consume() direto); este teste
// precisa do listener real rodando para exercitar o error handler do container.
@SpringBootTest(properties = {
    "spring.kafka.consumer.auto-offset-reset=earliest",
    "spring.kafka.listener.auto-startup=true"
})
// A DLT NAO entra aqui de proposito: ela tem de nascer do NewTopic da aplicacao.
// Com auto-create desligado, um destino errado no resolver falha o teste em vez
// de ser criado por baixo do pano.
@EmbeddedKafka(
    partitions = 1,
    topics = {"hospital.person"},
    brokerProperties = {"auto.create.topics.enable=false"}
)
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class PersonEventConsumerRecoveryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @MockitoSpyBean
    private PersonRegistrationParser parser;

    @MockitoSpyBean
    private ProvisionAccountFromPersonEvent provisionAccount;

    @Test
    void unparseableMessageIsRoutedToDltAndDoesNotLogPayload(CapturedOutput output) throws Exception {
        kafkaTemplate.send("hospital.person", "invalid-key", "{\"invalid\":\"json-secret-payload\"}")
            .get(10, TimeUnit.SECONDS);

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("dlt-group", "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                consumerProps, new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            // consumeFromAnEmbeddedTopic so aceita topico da lista do @EmbeddedKafka, e a
            // DLT nasce do NewTopic da aplicacao de proposito: assinar direto e o que
            // mantem o teste sensivel a um destino errado no resolver.
            consumer.subscribe(List.of("hospital.person.DLT"));
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

        Thread.sleep(1000);
        assertThat(output.getOut()).doesNotContain("json-secret-payload");
    }

    // Entrega eventual na DLT nao prova a classificacao: um malformado reentregue
    // tres vezes tambem chega la. O que separa os dois casos e a contagem.
    @Test
    void malformedIsDeliveredOnceWhileTransientFailureIsRetriedWithinTheBound() throws Exception {
        Mockito.doThrow(new IllegalStateException("banco indisponivel"))
            .when(provisionAccount).provision(Mockito.any());

        String validEnvelope = PersonEventFixtures.patientRegistered(
            UUID.randomUUID(), UUID.randomUUID(), "52998224726");

        kafkaTemplate.send("hospital.person", "k1", "nao-e-json").get(10, TimeUnit.SECONDS);
        kafkaTemplate.send("hospital.person", "k2", validEnvelope).get(10, TimeUnit.SECONDS);

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("dlt-count-group", "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // A DLT e compartilhada pela classe: contar registros sem filtrar deixaria o
        // poll parar com os do outro teste e verificar antes de o retry terminar.
        // Espera pelos dois payloads deste teste, e so entao conta as tentativas.
        Set<String> awaited = new HashSet<>(Set.of("nao-e-json", validEnvelope));
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                consumerProps, new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            consumer.subscribe(List.of("hospital.person.DLT"));
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
        Mockito.verify(provisionAccount, Mockito.times(3)).provision(Mockito.any());
    }
}
