package com.fiap.hospital.history.projection.api;

import com.fiap.hospital.history.projection.service.AppointmentProjectionQueryService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.time.Instant;
import java.util.UUID;

@Controller
public class AppointmentProjectionQuery {

    private final AppointmentProjectionQueryService queryService;

    public AppointmentProjectionQuery(AppointmentProjectionQueryService queryService) {
        this.queryService = queryService;
    }

    @QueryMapping
    public AppointmentHistory appointments(
            @Argument String patientId,
            @Argument Boolean futureOnly
    ) {
        UUID patient = patientId == null || patientId.isBlank() ? null : UUID.fromString(patientId);
        //criar interface do service e fazer inversao de dependencia
        return queryService.list(patient, Boolean.TRUE.equals(futureOnly), Instant.now());
    }
}
