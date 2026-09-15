package com.fiap.hospital.history.projection.consumer;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppointmentEventParserTest {

    private final AppointmentEventParser parser = new AppointmentEventParser();

    @Test
    void parsesScheduledEnvelope() {
        AppointmentScheduledMessage message = (AppointmentScheduledMessage) parser.parse(envelope(true, "motivo"));

        assertEquals(UUID.fromString("0b7f4e2a-9c3d-4a1e-8f55-2b6d1c9e7a04"), message.eventId());
        assertEquals(Instant.parse("2026-09-02T13:30:00.000Z"), message.occurredAt());
        assertEquals(UUID.fromString("7c1e5a93-2f84-4b60-8d17-a3e9c0524b6f"), message.appointmentId());
        assertTrue(message.fitIn());
        assertEquals("motivo", message.fitInReason());
        assertEquals("Ana Ribeiro", message.patientName());
    }

    @Test
    void blankFitInReasonBecomesNull() {
        assertNull(((AppointmentScheduledMessage) parser.parse(envelope(false, "  "))).fitInReason());
    }

    @Test
    void nullFitInReasonStaysNull() {
        assertNull(((AppointmentScheduledMessage) parser.parse(envelope(false, null))).fitInReason());
        assertFalse(((AppointmentScheduledMessage) parser.parse(envelope(false, null))).fitIn());
    }

    @Test
    void ignoresUnknownEventType() {
        UnsupportedAppointmentEventException ex = assertThrows(
                UnsupportedAppointmentEventException.class,
                () -> parser.parse(envelope(false, null).replace("AppointmentScheduled", "AppointmentUnknown"))
        );
        assertEquals("AppointmentUnknown", ex.getMessage());
    }

    @Test
    void rejectsEnvelopeWithoutData() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("""
                {"eventId":"0b7f4e2a-9c3d-4a1e-8f55-2b6d1c9e7a04","eventType":"AppointmentScheduled","occurredAt":"2026-09-02T13:30:00.000Z"}
                """));
    }

    @Test
    void rejectsNonObjectData() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("""
                {"eventId":"0b7f4e2a-9c3d-4a1e-8f55-2b6d1c9e7a04","eventType":"AppointmentScheduled","occurredAt":"2026-09-02T13:30:00.000Z","data":[]}
                """));
    }

    @Test
    void rejectsInvalidStatusAndMissingFields() {
        String base = envelope(false, null);
        assertThrows(IllegalArgumentException.class, () -> parser.parse(base.replace("\"status\": \"SCHEDULED\"", "\"status\": \"CANCELLED\"")));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(base.replace("\"patientName\": \"Ana Ribeiro\",\n", "")));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(base.replace("\"patientName\": \"Ana Ribeiro\"", "\"patientName\": null")));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(base.replace("\"patientName\": \"Ana Ribeiro\"", "\"patientName\": 1")));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(base.replace("\"patientName\": \"Ana Ribeiro\"", "\"patientName\": \" \"")));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(base.replace("\"fitIn\": false", "\"fitIn\": null")));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(base.replace("\"fitIn\": false,\n", "")));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(base.replace("\"fitIn\": false", "\"fitIn\": \"no\"")));
        assertThrows(RuntimeException.class, () -> parser.parse("{"));
    }

    // A excecao do Jackson repete o trecho recusado do documento. Sem envolver,
    // o token cru viaja na mensagem e na causa ate quem registrar a falha.
    @Test
    void malformedJsonIsRejectedWithoutEchoingTheDocument() {
        String leaky = "syntheticPatientSecret";

        MalformedAppointmentEventException exception = assertThrows(
                MalformedAppointmentEventException.class,
                () -> parser.parse("{" + leaky)
        );

        assertEquals("envelope não é JSON válido", exception.getMessage());
        assertNull(exception.getCause());
    }

    // UUID.fromString e Instant.parse repetem a entrada recusada na mensagem deles,
    // e essa entrada e payload do paciente. O parser tem de trocar por uma mensagem
    // que nomeia so o campo, senao o valor vaza para qualquer log que registre a causa.
    @Test
    void invalidUuidIsRejectedWithoutEchoingTheValue() {
        String base = envelope(false, null);
        String leaky = "paciente-maria@example.invalid";

        MalformedAppointmentEventException exception = assertThrows(
                MalformedAppointmentEventException.class,
                () -> parser.parse(base.replace("7c1e5a93-2f84-4b60-8d17-a3e9c0524b6f", leaky))
        );

        assertEquals("campo inválido: appointmentId", exception.getMessage());
        assertFalse(exception.getMessage().contains(leaky));
    }

    // DateTimeParseException nao e IllegalArgumentException: sem a conversao, uma data
    // invalida escapa da classificacao de nao-retryable e e reentregue tres vezes.
    @Test
    void invalidInstantIsRejectedAsMalformedNotAsDateTimeParseException() {
        String base = envelope(false, null);
        String leaky = "ontem-as-tres";

        MalformedAppointmentEventException exception = assertThrows(
                MalformedAppointmentEventException.class,
                () -> parser.parse(base.replace("\"scheduledAt\": \"2026-09-02T13:30:00.000Z\"", "\"scheduledAt\": \"" + leaky + "\""))
        );

        assertEquals("campo inválido: scheduledAt", exception.getMessage());
        assertFalse(exception.getMessage().contains(leaky));
        assertTrue(IllegalArgumentException.class.isInstance(exception));
    }

    @Test
    void nonTextOrAbsentFitInReasonIsIgnored() {
        assertNull(((AppointmentScheduledMessage) parser.parse(envelope(false, null).replace("\"fitInReason\": null", "\"fitInReason\": 1"))).fitInReason());
        assertNull(((AppointmentScheduledMessage) parser.parse(envelope(false, null).replace("\"fitInReason\": null,\n", ""))).fitInReason());
    }

    private static String envelope(boolean fitIn, String fitInReason) {
        String reason = fitInReason == null ? "null" : "\"" + fitInReason + "\"";
        return """
                {
                  "eventId": "0b7f4e2a-9c3d-4a1e-8f55-2b6d1c9e7a04",
                  "eventType": "AppointmentScheduled",
                  "eventVersion": 1,
                  "occurredAt": "2026-09-02T13:30:00.000Z",
                  "data": {
                    "appointmentId": "7c1e5a93-2f84-4b60-8d17-a3e9c0524b6f",
                    "patientId": "3f2b8c10-5d47-4e91-9a2e-7c6f1b0d8e33",
                    "doctorId": "b91c4d72-8a05-4f36-b1de-0e5a72c4f118",
                    "scheduledAt": "2026-09-02T13:30:00.000Z",
                    "status": "SCHEDULED",
                    "fitIn": %s,
                    "fitInReason": %s,
                    "patientName": "Ana Ribeiro",
                    "doctorName": "Dr. Paulo Menezes",
                    "doctorSpecialty": "Cardiologia"
                  }
                }
                """.formatted(fitIn, reason);
    }
    @Test
    void parsesRescheduledEnvelope() {
        String json = """
            {
              "eventId": "11111111-2222-3333-4444-555555555555",
              "eventType": "AppointmentRescheduled",
              "occurredAt": "2026-07-09T10:00:00Z",
              "data": {
                "appointmentId": "00000000-0000-4000-8000-000000000001",
                "patientId": "00000000-0000-4000-8000-000000000002",
                "doctorId": "00000000-0000-4000-8000-000000000003",
                "previousScheduledAt": "2026-07-10T14:00:00Z",
                "scheduledAt": "2026-07-11T14:00:00Z",
                "status": "SCHEDULED",
                "fitIn": false,
                "fitInReason": null,
                "patientName": "Paciente",
                "doctorName": "Medico",
                "doctorSpecialty": "Especialidade"
              }
            }
        """;
        AppointmentRescheduledMessage message = (AppointmentRescheduledMessage) parser.parse(json);
        assertEquals(Instant.parse("2026-07-10T14:00:00Z"), message.previousScheduledAt());
        assertEquals(Instant.parse("2026-07-11T14:00:00Z"), message.scheduledAt());
    }

    @Test
    void parsesCancelledEnvelope() {
        String json = """
            {
              "eventId": "11111111-2222-3333-4444-555555555555",
              "eventType": "AppointmentCancelled",
              "occurredAt": "2026-07-09T10:00:00Z",
              "data": {
                "appointmentId": "00000000-0000-4000-8000-000000000001",
                "patientId": "00000000-0000-4000-8000-000000000002",
                "doctorId": "00000000-0000-4000-8000-000000000003",
                "scheduledAt": "2026-07-11T14:00:00Z",
                "cancelledAt": "2026-07-09T11:00:00Z",
                "status": "CANCELLED"
              }
            }
        """;
        AppointmentCancelledMessage message = (AppointmentCancelledMessage) parser.parse(json);
        assertEquals(Instant.parse("2026-07-09T11:00:00Z"), message.cancelledAt());
    }

    @Test
    void parsesCompletedEnvelope() {
        String json = """
            {
              "eventId": "11111111-2222-3333-4444-555555555555",
              "eventType": "AppointmentCompleted",
              "occurredAt": "2026-07-09T10:00:00Z",
              "data": {
                "appointmentId": "00000000-0000-4000-8000-000000000001",
                "patientId": "00000000-0000-4000-8000-000000000002",
                "doctorId": "00000000-0000-4000-8000-000000000003",
                "scheduledAt": "2026-07-11T14:00:00Z",
                "completedAt": "2026-07-09T11:00:00Z",
                "status": "COMPLETED"
              }
            }
        """;
        AppointmentCompletedMessage message = (AppointmentCompletedMessage) parser.parse(json);
        assertEquals(Instant.parse("2026-07-09T11:00:00Z"), message.completedAt());
    }
}
