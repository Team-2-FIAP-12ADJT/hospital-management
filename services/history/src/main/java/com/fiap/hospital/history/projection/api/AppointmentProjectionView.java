package com.fiap.hospital.history.projection.api;

import com.fiap.hospital.history.projection.domain.AppointmentStatus;

public record AppointmentProjectionView(
        String appointmentId,
        String patientId,
        String doctorId,
        String scheduledAt,
        AppointmentStatus status,
        boolean fitIn,
        String fitInReason,
        String patientName,
        String doctorName,
        String doctorSpecialty,
        String cancelledAt,
        String completedAt
) {
}
