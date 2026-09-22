package com.example.issuedjinn.rest.api.dto;

import com.example.issuedjinn.dto.CommentDto;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * DTO for Comment list responses.
 */
public class CommentsListDto {

    public final List<CommentDto> comments;

    @JsonCreator
    public CommentsListDto(@JsonProperty("comments") List<CommentDto> comments) {
        this.comments = comments != null ? comments : List.of();
    }
}
