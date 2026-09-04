package com.fiap.hospital.history.projection.service;

import com.fiap.hospital.history.projection.api.AppointmentHistory;
import com.fiap.hospital.history.projection.api.AppointmentProjectionView;
import com.fiap.hospital.history.projection.domain.AppointmentProjection;
import com.fiap.hospital.history.projection.domain.AppointmentStatus;
import com.fiap.hospital.history.projection.domain.ProjectionFreshness;
import com.fiap.hospital.history.projection.repository.AppointmentProjectionRepository;
import com.fiap.hospital.history.projection.repository.ProjectionFreshnessRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AppointmentProjectionQueryService {

    static final int MAX_SIZE = 50;

    private final AppointmentProjectionRepository appointments;
    private final ProjectionFreshnessRepository freshness;

    public AppointmentProjectionQueryService(
            AppointmentProjectionRepository appointments,
            ProjectionFreshnessRepository freshness
    ) {
        this.appointments = appointments;
        this.freshness = freshness;
    }

    @Transactional(readOnly = true)
    public AppointmentHistory list(
            UUID requestedPatientId,
            boolean futureOnly,
            int page,
            int size,
            Instant now,
            UUID subject,
            String role
    ) {
        UUID patientId = resolvePatientScope(role, subject, requestedPatientId);
        requirePageSize(page, size);
        Pageable pageable = PageRequest.of(
                page - 1,
                size,
                Sort.by(Sort.Direction.DESC, "scheduledAt")
        );
        Page<AppointmentProjection> result = futureOnly
                ? appointments.findByPatientIdAndStatusAndScheduledAtGreaterThanEqual(
                        patientId, AppointmentStatus.SCHEDULED, now, pageable)
                : appointments.findByPatientId(patientId, pageable);

        List<AppointmentProjectionView> views = result.getContent().stream()
                .map(this::toView)
                .toList();

        String lastApplied = freshness.findById((short) 1)
                .map(ProjectionFreshness::getLastAppliedAt)
                .map(InstantFormats::isoMillis)
                .orElse(null);

        return new AppointmentHistory(
                lastApplied,
                views,
                page,
                result.getSize(),
                Math.toIntExact(result.getTotalElements()),
                result.getTotalPages()
        );
    }

    static void requirePageSize(int page, int size) {
        if (page < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page deve ser maior ou igual a 1");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size deve estar entre 1 e " + MAX_SIZE);
        }
    }

    static UUID resolvePatientScope(String role, UUID subject, UUID requestedPatientId) {
        if (role == null || role.isBlank()) {
            throw new AccessDeniedException("Token sem Role");
        }
        if ("PATIENT".equals(role)) {
            return subject;
        }
        if ("DOCTOR".equals(role) || "NURSE".equals(role)) {
            if (requestedPatientId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patientId é obrigatório");
            }
            return requestedPatientId;
        }
        throw new AccessDeniedException("Papel não autorizado a consultar histórico");
    }

    static boolean isUpcoming(AppointmentProjection row, Instant now) {
        return row.getStatus() == AppointmentStatus.SCHEDULED
                && !row.getScheduledAt().isBefore(now);
    }

    private AppointmentProjectionView toView(AppointmentProjection row) {
        return new AppointmentProjectionView(
                row.getAppointmentId().toString(),
                row.getPatientId().toString(),
                row.getDoctorId().toString(),
                InstantFormats.isoMillis(row.getScheduledAt()),
                row.getStatus(),
                row.isFitIn(),
                row.getFitInReason(),
                row.getPatientName(),
                row.getDoctorName(),
                row.getDoctorSpecialty(),
                InstantFormats.isoMillis(row.getCancelledAt()),
                InstantFormats.isoMillis(row.getCompletedAt())
        );
    }
}
