package com.fiap.hospital.history.projection.api;

import java.util.List;

public record AppointmentHistory(
        String projectionFreshness,
        List<AppointmentProjectionView> appointments
) {
}
