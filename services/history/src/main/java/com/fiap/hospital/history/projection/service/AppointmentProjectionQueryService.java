package com.fiap.hospital.history.projection.service;

import com.fiap.hospital.history.projection.api.AppointmentHistory;
import com.fiap.hospital.history.projection.api.AppointmentProjectionView;
import com.fiap.hospital.history.projection.domain.AppointmentProjection;
import com.fiap.hospital.history.projection.domain.AppointmentStatus;
import com.fiap.hospital.history.projection.domain.ProjectionFreshness;
import com.fiap.hospital.history.projection.repository.AppointmentProjectionRepository;
import com.fiap.hospital.history.projection.repository.ProjectionFreshnessRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AppointmentProjectionQueryService {

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
    public AppointmentHistory list(UUID patientId, boolean futureOnly, Instant now) {
        List<AppointmentProjection> rows = patientId == null
                ? appointments.findAllByOrderByScheduledAtDesc()
                : appointments.findByPatientIdOrderByScheduledAtDesc(patientId);

        List<AppointmentProjectionView> views = rows.stream()
                .filter(row -> !futureOnly || isUpcoming(row, now))
                .map(this::toView)
                .toList();

        String lastApplied = freshness.findById((short) 1)
                .map(ProjectionFreshness::getLastAppliedAt)
                .map(InstantFormats::isoMillis)
                .orElse(null);

        return new AppointmentHistory(lastApplied, views);
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
