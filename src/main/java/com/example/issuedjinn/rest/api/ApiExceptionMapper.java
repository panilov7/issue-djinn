package com.example.issuedjinn.rest.api;

import com.example.issuedjinn.domain.service.IssueService;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Exception mapper for REST API errors.
 * Maps domain exceptions to appropriate HTTP status codes and error envelopes.
 */
@Provider
public class ApiExceptionMapper implements ExceptionMapper<Exception> {

    @Override
    public Response toResponse(Exception exception) {
        String message = exception.getMessage() != null ? exception.getMessage() : "Invalid request";

        // Handle NotFoundException — 404
        if (exception instanceof IssueService.NotFoundException) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ErrorEnvelope.of("ISSUE_NOT_FOUND", message))
                    .type("application/json")
                    .build();
        }

        // Handle AlreadyClaimedException — 409
        if (exception instanceof IssueService.AlreadyClaimedException) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(ErrorEnvelope.of("ALREADY_CLAIMED", message))
                    .type("application/json")
                    .build();
        }

        // Handle InvalidStatusTransitionException — 409
        if (exception instanceof IssueService.InvalidStatusTransitionException) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(ErrorEnvelope.of("INVALID_STATUS_TRANSITION", message))
                    .type("application/json")
                    .build();
        }

        // Handle CycleDetectedException from IssueService — 409
        if (exception instanceof IssueService.CycleDetectedException) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(ErrorEnvelope.of("CYCLE_DETECTED", message))
                    .type("application/json")
                    .build();
        }

        // Handle HierarchyDepthExceededException from IssueService — 409
        if (exception instanceof IssueService.HierarchyDepthExceededException) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(ErrorEnvelope.of("HIERARCHY_DEPTH_EXCEEDED", message))
                    .type("application/json")
                    .build();
        }

        // Handle validation errors (IllegalArgumentException, IllegalStateException) — 400
        if (exception instanceof IllegalArgumentException || exception instanceof IllegalStateException) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ErrorEnvelope.of("VALIDATION_ERROR", message))
                    .type("application/json")
                    .build();
        }

        // Default: internal server error — 500
        String errMsg = exception.getMessage() != null ? exception.getMessage() : "Internal server error";
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ErrorEnvelope.of("INTERNAL_ERROR", errMsg))
                .type("application/json")
                .build();
    }
}