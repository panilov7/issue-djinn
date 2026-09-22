package com.example.issuedjinn.dto;

import com.example.issuedjinn.domain.entity.Issue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * DTO for Issue responses (detail shape).
 * Used by both REST API and MCP tools.
 */
public class IssueDto {

    public final Long id;
    public final String title;
    public final String description;
    public final String status;
    public final String assignee;
    public final Instant createdAt;
    public final Instant updatedAt;
    public final Long parentId;
    public final Set<String> labels;
    public final Integer commentCount;
    public final Set<DependencyDto> dependencies;
    public final Set<DependencyDto> dependents;
    public final List<IssueListDto.IssueSummaryDto> children;

    @JsonCreator
    public IssueDto(
            @JsonProperty("id") Long id,
            @JsonProperty("title") String title,
            @JsonProperty("description") String description,
            @JsonProperty("status") String status,
            @JsonProperty("assignee") String assignee,
            @JsonProperty("createdAt") Instant createdAt,
            @JsonProperty("updatedAt") Instant updatedAt,
            @JsonProperty("parentId") Long parentId,
            @JsonProperty("labels") Set<String> labels,
            @JsonProperty("commentCount") Integer commentCount,
            @JsonProperty("dependencies") Set<DependencyDto> dependencies,
            @JsonProperty("dependents") Set<DependencyDto> dependents,
            @JsonProperty("children") List<IssueListDto.IssueSummaryDto> children) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.status = status;
        this.assignee = assignee;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.parentId = parentId;
        this.labels = labels;
        this.commentCount = commentCount;
        this.dependencies = dependencies;
        this.dependents = dependents;
        this.children = children;
    }

    public static IssueDto fromIssue(Issue issue) {
        return fromIssue(issue, List.of());
    }

    /**
     * Map an issue together with the children its detail payload embeds. Only the
     * detail endpoints have children to hand over; the mutation endpoints map
     * without them, and the detail page refetches after every mutation.
     */
    public static IssueDto fromIssue(Issue issue, List<Issue> children) {
        Set<String> labels = issue.labels;
        int commentCount = issue.comments != null ? issue.comments.size() : 0;

        Set<DependencyDto> deps = issue.dependencies != null
                ? issue.dependencies.stream()
                .map(d -> new DependencyDto(d.id, d.title, d.status))
                .collect(Collectors.toSet())
                : Set.of();

        Set<DependencyDto> dependents = issue.dependents != null
                ? issue.dependents.stream()
                .map(d -> new DependencyDto(d.id, d.title, d.status))
                .collect(Collectors.toSet())
                : Set.of();

        List<IssueListDto.IssueSummaryDto> childRows = children.stream()
                .map(IssueListDto.IssueSummaryDto::fromIssue)
                .toList();

        return new IssueDto(
                issue.id,
                issue.title,
                issue.description,
                issue.status,
                issue.assignee,
                issue.createdAt,
                issue.updatedAt,
                issue.parent != null ? issue.parent.id : null,
                labels,
                commentCount,
                deps,
                dependents,
                childRows
        );
    }
}
