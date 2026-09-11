package com.fiap.hospital.history.projection.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentEventConsumerTest {

    @Mock
    private AppointmentEventParser parser;

    @Mock
    private ApplyAppointmentScheduled applyScheduled;

    @Test
    void appliesParsedMessage() {
        AppointmentScheduledMessage message = sample();
        when(parser.parse("json")).thenReturn(message);
        AppointmentEventConsumer consumer = new AppointmentEventConsumer(parser, applyScheduled);

        consumer.consume("json");

        verify(applyScheduled).apply(message);
    }

    @Test
    void receiveReadsRecordValue() {
        AppointmentScheduledMessage message = sample();
        when(parser.parse("payload")).thenReturn(message);
        AppointmentEventConsumer consumer = new AppointmentEventConsumer(parser, applyScheduled);

        consumer.receive(new ConsumerRecord<>("hospital.appointment", 2, 9L, "key", "payload"));

        verify(applyScheduled).apply(message);
    }

    @Test
    void ignoresUnsupportedType() {
        when(parser.parse("json")).thenThrow(new UnsupportedAppointmentEventException("AppointmentCancelled"));
        AppointmentEventConsumer consumer = new AppointmentEventConsumer(parser, applyScheduled);

        consumer.consume("json");

        verifyNoInteractions(applyScheduled);
    }

    @Test
    void discardsUnparseableMessage() {
        when(parser.parse("bad")).thenThrow(new IllegalArgumentException("campo obrigatório ausente: patientName"));
        AppointmentEventConsumer consumer = new AppointmentEventConsumer(parser, applyScheduled);

        consumer.consume("bad");

        verifyNoInteractions(applyScheduled);
    }

    private static AppointmentScheduledMessage sample() {
        return new AppointmentScheduledMessage(
                UUID.fromString("0b7f4e2a-9c3d-4a1e-8f55-2b6d1c9e7a04"),
                Instant.parse("2026-09-02T13:30:00.000Z"),
                UUID.fromString("7c1e5a93-2f84-4b60-8d17-a3e9c0524b6f"),
                UUID.fromString("3f2b8c10-5d47-4e91-9a2e-7c6f1b0d8e33"),
                UUID.fromString("b91c4d72-8a05-4f36-b1de-0e5a72c4f118"),
                Instant.parse("2026-09-02T13:30:00.000Z"),
                false,
                null,
                "Ana Ribeiro",
                "Dr. Paulo Menezes",
                "Cardiologia"
        );
    }
}
