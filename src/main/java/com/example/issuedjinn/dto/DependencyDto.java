package com.example.issuedjinn.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Summary DTO for a dependency relationship.
 */
public class DependencyDto {

    public final Long id;
    public final String title;
    public final String status;

    @JsonCreator
    public DependencyDto(
            @JsonProperty("id") Long id,
            @JsonProperty("title") String title,
            @JsonProperty("status") String status) {
        this.id = id;
        this.title = title;
        this.status = status;
    }
}
