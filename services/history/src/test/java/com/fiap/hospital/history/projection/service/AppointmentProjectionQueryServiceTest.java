package com.fiap.hospital.history.projection.service;

import com.fiap.hospital.history.projection.api.AppointmentHistory;
import com.fiap.hospital.history.projection.domain.AppointmentProjection;
import com.fiap.hospital.history.projection.domain.AppointmentStatus;
import com.fiap.hospital.history.projection.domain.ProjectionFreshness;
import com.fiap.hospital.history.projection.repository.AppointmentProjectionRepository;
import com.fiap.hospital.history.projection.repository.ProjectionFreshnessRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

@ExtendWith(MockitoExtension.class)
class AppointmentProjectionQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-25T12:00:00Z");
    private static final UUID PATIENT = UUID.fromString("00000000-0000-4000-8000-000000000003");
    private static final UUID OTHER = UUID.fromString("00000000-0000-4000-8000-000000000099");

    @Mock
    private AppointmentProjectionRepository appointments;

    @Mock
    private ProjectionFreshnessRepository freshness;

    private AppointmentProjectionQueryService service;

    @BeforeEach
    void setUp() {
        service = new AppointmentProjectionQueryService(appointments, freshness);
    }

    @Test
    void upcomingRequiresScheduledStatusAndTimeNotBeforeNow() {
        assertTrue(AppointmentProjectionQueryService.isUpcoming(row(AppointmentStatus.SCHEDULED, "2026-08-25T12:00:00Z"), NOW));
        assertTrue(AppointmentProjectionQueryService.isUpcoming(row(AppointmentStatus.SCHEDULED, "2026-09-15T13:30:00Z"), NOW));
        assertFalse(AppointmentProjectionQueryService.isUpcoming(row(AppointmentStatus.SCHEDULED, "2026-08-01T10:00:00Z"), NOW));
        assertFalse(AppointmentProjectionQueryService.isUpcoming(row(AppointmentStatus.CANCELLED, "2026-09-05T09:00:00Z"), NOW));
        assertFalse(AppointmentProjectionQueryService.isUpcoming(row(AppointmentStatus.COMPLETED, "2026-07-10T14:00:00Z"), NOW));
    }

    @Test
    void patientUsesSubjectWhenArgumentOmitted() {
        assertEquals(PATIENT, AppointmentProjectionQueryService.resolvePatientScope("PATIENT", PATIENT, null));
    }

    @Test
    void patientAlwaysUsesSubjectAndIgnoresRequestedId() {
        assertEquals(PATIENT, AppointmentProjectionQueryService.resolvePatientScope("PATIENT", PATIENT, OTHER));
    }

    @Test
    void doctorRequiresPatientId() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> AppointmentProjectionQueryService.resolvePatientScope("DOCTOR", PATIENT, null)
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void doctorFiltersByRequestedPatientId() {
        assertEquals(OTHER, AppointmentProjectionQueryService.resolvePatientScope("DOCTOR", PATIENT, OTHER));
    }

    @Test
    void nurseUsesRequestedPatientId() {
        assertEquals(OTHER, AppointmentProjectionQueryService.resolvePatientScope("NURSE", PATIENT, OTHER));
    }

    @Test
    void blankRoleIsDenied() {
        assertThrows(
                AccessDeniedException.class,
                () -> AppointmentProjectionQueryService.resolvePatientScope(" ", PATIENT, null)
        );
    }

    @Test
    void nullRoleIsDenied() {
        assertThrows(
                AccessDeniedException.class,
                () -> AppointmentProjectionQueryService.resolvePatientScope(null, PATIENT, null)
        );
    }

    @Test
    void unknownRoleIsDenied() {
        assertThrows(
                AccessDeniedException.class,
                () -> AppointmentProjectionQueryService.resolvePatientScope("ADMIN", PATIENT, OTHER)
        );
    }

    @Test
    void requirePageSizeRejectsInvalidValues() {
        assertThrows(
                ResponseStatusException.class,
                () -> AppointmentProjectionQueryService.requirePageSize(0, 20)
        );
        assertThrows(
                ResponseStatusException.class,
                () -> AppointmentProjectionQueryService.requirePageSize(1, 0)
        );
        assertThrows(
                ResponseStatusException.class,
                () -> AppointmentProjectionQueryService.requirePageSize(1, 51)
        );
        AppointmentProjectionQueryService.requirePageSize(1, 20);
        AppointmentProjectionQueryService.requirePageSize(2, 50);
    }

    @Test
    void listRejectsInvalidPageBeforeQuerying() {
        assertThrows(
                ResponseStatusException.class,
                () -> service.list(null, false, 0, 10, NOW, PATIENT, "PATIENT")
        );
    }

    @Test
    void listsAllAppointmentsForPatientWithPaginationMetadata() {
        when(appointments.findByPatientId(eq(PATIENT), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(fullRow()), PageRequest.of(0, 10), 11));
        when(freshness.findById((short) 1)).thenReturn(Optional.of(freshnessAt(NOW)));

        AppointmentHistory history = service.list(null, false, 1, 10, NOW, PATIENT, "PATIENT");

        assertEquals(1, history.page());
        assertEquals(10, history.size());
        assertEquals(11, history.totalElements());
        assertEquals(2, history.totalPages());
        assertEquals(NOW.toString(), history.projectionFreshness());
        assertEquals(1, history.appointments().size());
        assertEquals("Marcos Vieira", history.appointments().getFirst().patientName());
        assertEquals(AppointmentStatus.COMPLETED, history.appointments().getFirst().status());
        assertEquals("motivo", history.appointments().getFirst().fitInReason());
    }

    @Test
    void listsUpcomingFromDatabaseWhenFutureOnly() {
        when(appointments.findByPatientIdAndStatusAndScheduledAtGreaterThanEqual(
                eq(PATIENT), eq(AppointmentStatus.SCHEDULED), eq(NOW), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 5), 0));
        when(freshness.findById((short) 1)).thenReturn(Optional.empty());

        AppointmentHistory history = service.list(PATIENT, true, 1, 5, NOW, PATIENT, "DOCTOR");

        assertNull(history.projectionFreshness());
        assertTrue(history.appointments().isEmpty());
        assertEquals(0, history.totalElements());
        verify(appointments).findByPatientIdAndStatusAndScheduledAtGreaterThanEqual(
                eq(PATIENT), eq(AppointmentStatus.SCHEDULED), eq(NOW), any(Pageable.class));
    }

    private static AppointmentProjection row(AppointmentStatus status, String scheduledAt) {
        AppointmentProjection projection = new AppointmentProjection();
        setField(projection, "status", status);
        setField(projection, "scheduledAt", Instant.parse(scheduledAt));
        return projection;
    }

    private static AppointmentProjection fullRow() {
        AppointmentProjection projection = new AppointmentProjection();
        setField(projection, "appointmentId", UUID.fromString("10000000-0000-4000-8000-000000000003"));
        setField(projection, "patientId", PATIENT);
        setField(projection, "doctorId", UUID.fromString("00000000-0000-4000-8000-000000000001"));
        setField(projection, "scheduledAt", Instant.parse("2026-07-10T14:00:00.123456Z"));
        setField(projection, "status", AppointmentStatus.COMPLETED);
        setField(projection, "fitIn", true);
        setField(projection, "fitInReason", "motivo");
        setField(projection, "patientName", "Marcos Vieira");
        setField(projection, "doctorName", "Dra. Helena Prado");
        setField(projection, "doctorSpecialty", "Cardiologia");
        setField(projection, "cancelledAt", Instant.parse("2026-07-09T10:00:00Z"));
        setField(projection, "completedAt", Instant.parse("2026-07-10T14:42:00Z"));
        return projection;
    }

    private static ProjectionFreshness freshnessAt(Instant instant) {
        try {
            var constructor = ProjectionFreshness.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            ProjectionFreshness freshness = constructor.newInstance();
            setField(freshness, "id", (short) 1);
            setField(freshness, "lastAppliedAt", instant);
            return freshness;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
