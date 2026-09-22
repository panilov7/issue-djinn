package com.example.issuedjinn.rest.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * DTO for Labels response.
 */
public class LabelsDto {
    
    public final List<String> labels;

    @JsonCreator
    public LabelsDto(@JsonProperty("labels") List<String> labels) {
        this.labels = labels != null ? labels : List.of();
    }
}
