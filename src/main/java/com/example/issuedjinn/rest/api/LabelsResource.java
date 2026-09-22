package com.example.issuedjinn.rest.api;

import com.example.issuedjinn.domain.repository.IssueRepository;
import com.example.issuedjinn.rest.api.dto.LabelsDto;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * REST resource for Labels.
 */
@Path("/labels")
@Produces(MediaType.APPLICATION_JSON)
public class LabelsResource {

    @Inject
    IssueRepository issueRepository;

    /**
     * Get all labels in use.
     */
    @GET
    public Response getLabels() {
        // Query all distinct labels from issue_labels table
        // Using native query since ElementCollection doesn't have a built-in way to get distinct labels
        String sql = "SELECT DISTINCT label FROM issue_labels ORDER BY label";
        List<String> labels = issueRepository.getEntityManager()
                .createNativeQuery(sql, String.class)
                .getResultList();

        return Response.ok(new LabelsDto(labels)).build();
    }
}
