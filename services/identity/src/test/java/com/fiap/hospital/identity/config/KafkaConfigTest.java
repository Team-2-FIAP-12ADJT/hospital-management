package com.fiap.hospital.identity.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.support.ProducerListener;

/**
 * LoggingProducerListener não expõe getter para {@code includeContents}
 * (verificado via javap no jar do spring-kafka): a prova é comportamental,
 * não reflection sobre o campo privado.
 */
@ExtendWith(OutputCaptureExtension.class)
class KafkaConfigTest {

    private static final String SENSITIVE_MARKER = "cpf-52998224726-marker";

    @Test
    void producerListenerOmitsRecordContentsWhenPublishFails(CapturedOutput output) {
        ProducerListener<Object, Object> listener = new KafkaConfig().producerListener();

        ProducerRecord<Object, Object> record =
            new ProducerRecord<>("hospital.person.DLT", 0, "key-" + SENSITIVE_MARKER, "value-" + SENSITIVE_MARKER);
        listener.onError(record, null, new IllegalStateException("broker unavailable"));

        assertThat(output.getOut()).doesNotContain(SENSITIVE_MARKER);
    }
}
