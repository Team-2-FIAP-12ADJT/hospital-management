package com.fiap.hospital.history.projection.api;

import graphql.GraphQLError;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppointmentProjectionGraphQlExceptionHandlerTest {

    private final AppointmentProjectionGraphQlExceptionHandler handler =
            new AppointmentProjectionGraphQlExceptionHandler();

    @Test
    void mapsAccessDeniedToForbidden() {
        GraphQLError error = handler.onAccessDenied(new AccessDeniedException("Token sem Role"));
        assertEquals(ErrorType.FORBIDDEN, error.getErrorType());
        assertEquals("Token sem Role", error.getMessage());
    }

    @Test
    void mapsResponseStatusReasonWhenPresent() {
        GraphQLError error = handler.onBadRequest(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "patientId é obrigatório")
        );
        assertEquals(ErrorType.BAD_REQUEST, error.getErrorType());
        assertEquals("patientId é obrigatório", error.getMessage());
    }

    @Test
    void mapsResponseStatusMessageWhenReasonIsAbsent() {
        GraphQLError error = handler.onBadRequest(new ResponseStatusException(HttpStatus.BAD_REQUEST));
        assertEquals(ErrorType.BAD_REQUEST, error.getErrorType());
        assertEquals(new ResponseStatusException(HttpStatus.BAD_REQUEST).getMessage(), error.getMessage());
    }
}
