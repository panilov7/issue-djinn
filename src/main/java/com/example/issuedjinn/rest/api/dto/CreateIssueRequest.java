package com.example.issuedjinn.rest.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import java.util.Set;

/**
 * Request DTO for creating an issue.
 */
public class CreateIssueRequest {

    @NotBlank(message = "Title is required")
    public final String title;
    public final String description;
    public final Long parentId;
    public final Set<String> labels;

    @JsonCreator
    public CreateIssueRequest(
            @JsonProperty("title") String title,
            @JsonProperty("description") String description,
            @JsonProperty("parentId") Long parentId,
            @JsonProperty("labels") Set<String> labels) {
        this.title = title;
        this.description = description != null ? description : "";
        this.parentId = parentId;
        this.labels = labels;
    }
}
