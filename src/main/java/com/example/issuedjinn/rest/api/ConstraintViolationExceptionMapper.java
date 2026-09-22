package com.example.issuedjinn.rest.api;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.stream.Collectors;

/**
 * Exception mapper for Bean Validation failures.
 * Maps {@link ConstraintViolationException} to a 400 Bad Request with the
 * standard error envelope {@code {"error": {"code": "VALIDATION_ERROR", "message": "..."}}}.
 */
@Provider
public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .distinct()
                .collect(Collectors.joining("; "));

        if (message.isEmpty()) {
            message = "Invalid request";
        }

        return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorEnvelope.of("VALIDATION_ERROR", message))
                .type("application/json")
                .build();
    }
}
