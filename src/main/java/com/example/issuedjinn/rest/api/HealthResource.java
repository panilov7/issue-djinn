package com.example.issuedjinn.rest.api;

import com.example.issuedjinn.rest.api.dto.HealthDto;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST resource for Health checks.
 */
@Path("/health")
@Produces(MediaType.APPLICATION_JSON)
public class HealthResource {

    /**
     * Health check endpoint.
     */
    @GET
    public Response health() {
        return Response.ok(new HealthDto("ok")).build();
    }
}
