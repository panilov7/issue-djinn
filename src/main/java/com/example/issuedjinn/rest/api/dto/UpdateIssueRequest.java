package com.example.issuedjinn.rest.api.dto;

import com.example.issuedjinn.domain.service.IssueService;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request DTO for updating an issue (PATCH).
 * All fields are optional: an absent field means "don't change this field".
 *
 * The one exception is parentId, where an explicit null is meaningful — it removes
 * the parent. Absent keeps the current parent, null unparents, and an integer sets
 * or replaces it; {@link #parentUpdate()} turns those three cases into the
 * {@link IssueService.ParentUpdate} the service layer understands.
 */
public class UpdateIssueRequest {

    public final String title;
    public final String description;
    public final String assignee;
    @Size(max = 100, message = "Labels must not exceed 100 entries")
    public final List<String> labels;
    /** Raw JSON node so "field absent" can be distinguished from "field present with null". */
    public final JsonNode parentId;

    @JsonCreator
    public UpdateIssueRequest(
            @JsonProperty("title") String title,
            @JsonProperty("description") String description,
            @JsonProperty("assignee") String assignee,
            @JsonProperty("labels") List<String> labels,
            @JsonProperty("parentId") JsonNode parentId) {
        this.title = title;
        this.description = description;
        this.assignee = assignee;
        this.labels = labels;
        this.parentId = parentId;
    }

    /**
     * The requested parent change: field absent = don't change, explicit null =
     * unparent, integer = set or replace.
     */
    public IssueService.ParentUpdate parentUpdate() {
        if (parentId == null) {
            return IssueService.ParentUpdate.unchanged();
        }
        if (parentId.isNull()) {
            return IssueService.ParentUpdate.clear();
        }
        if (parentId.isIntegralNumber()) {
            return IssueService.ParentUpdate.set(parentId.asLong());
        }
        throw new IllegalArgumentException("parentId must be an integer or null");
    }
}
