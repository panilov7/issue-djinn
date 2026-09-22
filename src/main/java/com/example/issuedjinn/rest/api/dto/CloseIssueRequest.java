package com.example.issuedjinn.rest.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for closing an issue.
 * Both comment and author are required.
 */
public class CloseIssueRequest {
    
    @NotBlank(message = "Closing comment is required")
    public final String comment;
    @NotBlank(message = "Author is required")
    public final String author;

    @JsonCreator
    public CloseIssueRequest(@JsonProperty("comment") String comment,
                             @JsonProperty("author") String author) {
        this.comment = comment != null ? comment.trim() : "";
        this.author = author != null ? author.trim() : "";
    }
}
