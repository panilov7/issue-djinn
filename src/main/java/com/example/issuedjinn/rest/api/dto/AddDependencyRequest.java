package com.example.issuedjinn.rest.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for adding a dependency relationship.
 */
public class AddDependencyRequest {
    
    @NotNull(message = "Dependency id is required")
    public final Long dependencyId;

    @JsonCreator
    public AddDependencyRequest(@JsonProperty("dependency_id") Long dependencyId) {
        this.dependencyId = dependencyId;
    }
}
