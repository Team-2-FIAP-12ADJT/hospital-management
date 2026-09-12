package com.fiap.hospital.notification.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;

/**
 * Verifies the DLT/backoff wiring shared by every @KafkaListener in this module
 * through observable behavior only: no reflection into Spring Kafka internals.
 */
class KafkaErrorHandlingConfigTest {

    @Test
    void accountEventDeadLetterTopicIsDeclaredWithSinglePartitionAndReplica() {
        NewTopic topic = new KafkaErrorHandlingConfig().accountEventDeadLetterTopic();

        assertThat(topic.name()).isEqualTo("hospital.account.DLT");
        assertThat(topic.numPartitions()).isEqualTo(1);
        assertThat(topic.replicationFactor()).isEqualTo((short) 1);
    }

    @Test
    void personEventDeadLetterTopicIsDeclaredWithSinglePartitionAndReplica() {
        NewTopic topic = new KafkaErrorHandlingConfig().personEventDeadLetterTopic();

        assertThat(topic.name()).isEqualTo("hospital.person.DLT");
        assertThat(topic.numPartitions()).isEqualTo(1);
        assertThat(topic.replicationFactor()).isEqualTo((short) 1);
    }

    @Test
    void recovererSendsAccountEventFailureToItsOwnDeadLetterTopic() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<Object, Object> kafkaTemplate = mock(KafkaTemplate.class);
        DeadLetterPublishingRecoverer recoverer =
            KafkaErrorHandlingConfig.deadLetterRecoverer(kafkaTemplate);

        ConsumerRecord<Object, Object> record =
            new ConsumerRecord<>("hospital.account", 3, 42L, "key", "value");
        recoverer.accept(record, null, new IllegalStateException("boom"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProducerRecord<Object, Object>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        assertThat(captor.getValue().topic()).isEqualTo("hospital.account.DLT");
        assertThat(captor.getValue().partition()).isEqualTo(0);
    }

    @Test
    void recovererSendsPersonEventFailureToItsOwnDeadLetterTopic() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<Object, Object> kafkaTemplate = mock(KafkaTemplate.class);
        DeadLetterPublishingRecoverer recoverer =
            KafkaErrorHandlingConfig.deadLetterRecoverer(kafkaTemplate);

        ConsumerRecord<Object, Object> record =
            new ConsumerRecord<>("hospital.person", 7, 11L, "key", "value");
        recoverer.accept(record, null, new IllegalStateException("boom"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProducerRecord<Object, Object>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        assertThat(captor.getValue().topic()).isEqualTo("hospital.person.DLT");
        assertThat(captor.getValue().partition()).isEqualTo(0);
    }

    @Test
    void backOffRetriesFiveTimesWithExponentialDelayCappedAt30Seconds() {
        BackOff backOff = KafkaErrorHandlingConfig.backOff();
        BackOffExecution execution = backOff.start();

        assertThat(execution.nextBackOff()).isEqualTo(1000L);
        assertThat(execution.nextBackOff()).isEqualTo(2000L);
        assertThat(execution.nextBackOff()).isEqualTo(4000L);
        assertThat(execution.nextBackOff()).isEqualTo(8000L);
        assertThat(execution.nextBackOff()).isEqualTo(16000L);
        assertThat(execution.nextBackOff()).isEqualTo(BackOffExecution.STOP);
    }
}
