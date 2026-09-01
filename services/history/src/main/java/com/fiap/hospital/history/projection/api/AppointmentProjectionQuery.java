package com.fiap.hospital.history.projection.api;

import com.fiap.hospital.history.projection.service.AppointmentProjectionQueryService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
            @Argument Boolean futureOnly,
            @Argument int page,
            @Argument int size,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID patientIdFromToken = UUID.fromString(jwt.getSubject());
        String role = jwt.getClaimAsString("role");
        UUID requestedPatientId = patientId == null || patientId.isBlank()
                ? null
                : UUID.fromString(patientId);
        return queryService.list(
                requestedPatientId,
                Boolean.TRUE.equals(futureOnly),
                page,
                size,
                Instant.now(),
                patientIdFromToken,
                role
        );
    }
}
