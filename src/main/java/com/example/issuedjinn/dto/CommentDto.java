package com.example.issuedjinn.dto;

import com.example.issuedjinn.domain.entity.Comment;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * DTO for Comment responses.
 */
public class CommentDto {

    public final Long id;
    public final String author;
    public final String body;
    public final Instant createdAt;

    @JsonCreator
    public CommentDto(
            @JsonProperty("id") Long id,
            @JsonProperty("author") String author,
            @JsonProperty("body") String body,
            @JsonProperty("createdAt") Instant createdAt) {
        this.id = id;
        this.author = author;
        this.body = body;
        this.createdAt = createdAt;
    }

    public static CommentDto fromComment(Comment comment) {
        return new CommentDto(
                comment.id,
                comment.author,
                comment.body,
                comment.createdAt
        );
    }
}
