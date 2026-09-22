package com.example.issuedjinn.rest.api;

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.stream.Collectors;

/**
 * Maps unknown request-body fields to a 400 naming the offending field and the
 * accepted ones. Backed by quarkus.jackson.fail-on-unknown-properties=true:
 * a client-side rename must fail loudly, not silently drop the value.
 */
@Provider
public class UnknownFieldExceptionMapper implements ExceptionMapper<UnrecognizedPropertyException> {

    @Override
    public Response toResponse(UnrecognizedPropertyException exception) {
        String accepted = exception.getKnownPropertyIds().stream()
                .map(Object::toString)
                .sorted()
                .collect(Collectors.joining(", "));
        String message = "Unknown field: " + exception.getPropertyName()
                + ". Accepted fields: " + accepted;
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorEnvelope.of("UNKNOWN_FIELD", message))
                .type("application/json")
                .build();
    }
}
