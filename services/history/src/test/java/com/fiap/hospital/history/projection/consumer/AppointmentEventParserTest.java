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
        AppointmentScheduledMessage message = parser.parse(envelope(true, "motivo"));

        assertEquals(UUID.fromString("0b7f4e2a-9c3d-4a1e-8f55-2b6d1c9e7a04"), message.eventId());
        assertEquals(Instant.parse("2026-09-02T13:30:00.000Z"), message.occurredAt());
        assertEquals(UUID.fromString("7c1e5a93-2f84-4b60-8d17-a3e9c0524b6f"), message.appointmentId());
        assertTrue(message.fitIn());
        assertEquals("motivo", message.fitInReason());
        assertEquals("Ana Ribeiro", message.patientName());
    }

    @Test
    void blankFitInReasonBecomesNull() {
        assertNull(parser.parse(envelope(false, "  ")).fitInReason());
    }

    @Test
    void nullFitInReasonStaysNull() {
        assertNull(parser.parse(envelope(false, null)).fitInReason());
        assertFalse(parser.parse(envelope(false, null)).fitIn());
    }

    @Test
    void ignoresUnknownEventType() {
        UnsupportedAppointmentEventException ex = assertThrows(
                UnsupportedAppointmentEventException.class,
                () -> parser.parse(envelope(false, null).replace("AppointmentScheduled", "AppointmentCancelled"))
        );
        assertEquals("AppointmentCancelled", ex.getMessage());
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

    @Test
    void nonTextOrAbsentFitInReasonIsIgnored() {
        assertNull(parser.parse(envelope(false, null).replace("\"fitInReason\": null", "\"fitInReason\": 1")).fitInReason());
        assertNull(parser.parse(envelope(false, null).replace("\"fitInReason\": null,\n", "")).fitInReason());
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
}
