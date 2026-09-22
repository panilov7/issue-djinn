package com.example.issuedjinn.rest.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for Health response.
 */
public class HealthDto {
    
    public final String status;

    @JsonCreator
    public HealthDto(@JsonProperty("status") String status) {
        this.status = status;
    }
}
