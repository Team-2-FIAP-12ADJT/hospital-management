package com.fiap.hospital.history.projection.api;

import com.fiap.hospital.history.projection.domain.AppointmentStatus;
import com.fiap.hospital.history.projection.service.AppointmentProjectionQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentProjectionQueryTest {

    private static final UUID PATIENT = UUID.fromString("00000000-0000-4000-8000-000000000003");

    @Mock
    private AppointmentProjectionQueryService queryService;

    private AppointmentProjectionQuery query;

    @BeforeEach
    void setUp() {
        query = new AppointmentProjectionQuery(queryService);
    }

    @Test
    void mapsBlankPatientIdToNullAndFutureOnlyFalseWhenArgumentIsNull() {
        AppointmentHistory expected = emptyHistory();
        when(queryService.list(isNull(), eq(false), eq(1), eq(20), any(Instant.class), eq(PATIENT), eq("PATIENT")))
                .thenReturn(expected);

        AppointmentHistory actual = query.appointments("  ", null, 1, 20, jwt("PATIENT"));

        assertEquals(expected, actual);
    }

    @Test
    void parsesRequestedPatientIdAndFutureOnly() {
        UUID requested = UUID.fromString("00000000-0000-4000-8000-000000000099");
        when(queryService.list(eq(requested), eq(true), eq(2), eq(10), any(Instant.class), eq(PATIENT), eq("DOCTOR")))
                .thenReturn(emptyHistory());

        query.appointments(requested.toString(), true, 2, 10, jwt("DOCTOR"));

        ArgumentCaptor<Instant> now = ArgumentCaptor.forClass(Instant.class);
        verify(queryService).list(eq(requested), eq(true), eq(2), eq(10), now.capture(), eq(PATIENT), eq("DOCTOR"));
        assertFalse(now.getValue().isAfter(Instant.now().plusSeconds(2)));
    }

    @Test
    void treatsNullPatientIdAsOmitted() {
        when(queryService.list(isNull(), anyBoolean(), anyInt(), anyInt(), any(Instant.class), eq(PATIENT), eq("PATIENT")))
                .thenReturn(emptyHistory());

        query.appointments(null, false, 1, 10, jwt("PATIENT"));

        verify(queryService).list(isNull(), eq(false), eq(1), eq(10), any(Instant.class), eq(PATIENT), eq("PATIENT"));
    }

    private static Jwt jwt(String role) {
        Instant now = Instant.parse("2026-08-31T12:00:00Z");
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(PATIENT.toString())
                .claim("role", role)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .build();
    }

    private static AppointmentHistory emptyHistory() {
        return new AppointmentHistory(null, List.of(), 1, 10, 0, 0);
    }
}
