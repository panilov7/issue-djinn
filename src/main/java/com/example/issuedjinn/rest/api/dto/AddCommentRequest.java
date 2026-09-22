package com.example.issuedjinn.rest.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for adding a comment.
 */
public class AddCommentRequest {

    @NotBlank(message = "Author is required")
    public final String author;
    @NotBlank(message = "Comment body is required")
    public final String body;

    @JsonCreator
    public AddCommentRequest(
            @JsonProperty("author") String author,
            @JsonProperty("body") String body) {
        this.author = author;
        this.body = body;
    }
}
