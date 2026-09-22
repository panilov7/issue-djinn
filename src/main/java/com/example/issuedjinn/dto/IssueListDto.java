package com.example.issuedjinn.dto;

import com.example.issuedjinn.domain.entity.Issue;
import com.example.issuedjinn.domain.repository.ChildCounts;
import com.example.issuedjinn.domain.service.IssueService;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * DTO for Issue list responses (light shape).
 * Used by both REST API and MCP tools.
 */
public class IssueListDto {

    public final List<IssueListRowDto> issues;

    @JsonCreator
    public IssueListDto(@JsonProperty("issues") List<IssueListRowDto> issues) {
        this.issues = issues != null ? issues : List.of();
    }

    /**
     * Summary DTO for issue in list context.
     */
    public static class IssueSummaryDto {
        public final Long id;
        public final String title;
        public final String status;
        public final String assignee;
        public final Set<String> labels;
        public final Long parentId;
        public final Instant updatedAt;

        /**
         * The counts of the issue's first-level children. Embedded on list rows,
         * where it answers "how much work sits under this root?"; left out of the
         * detail payload's child rows, which are the lightest shape and whose
         * children the one-level rule keeps empty anyway.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public final ChildCounts childCounts;

        @JsonCreator
        public IssueSummaryDto(
                @JsonProperty("id") Long id,
                @JsonProperty("title") String title,
                @JsonProperty("status") String status,
                @JsonProperty("assignee") String assignee,
                @JsonProperty("labels") Set<String> labels,
                @JsonProperty("parentId") Long parentId,
                @JsonProperty("updatedAt") Instant updatedAt,
                @JsonProperty("childCounts") ChildCounts childCounts) {
            this.id = id;
            this.title = title;
            this.status = status;
            this.assignee = assignee;
            this.labels = labels != null ? labels : new HashSet<>();
            this.parentId = parentId;
            this.updatedAt = updatedAt;
            this.childCounts = childCounts;
        }

        public static IssueSummaryDto fromIssue(Issue issue) {
            return fromIssue(issue, null);
        }

        /**
         * Map an issue together with the child counts its list row embeds. Only
         * the list endpoints have counts to hand over; a detail payload's child
         * rows map without them.
         */
        public static IssueSummaryDto fromIssue(Issue issue, ChildCounts childCounts) {
            return new IssueSummaryDto(
                    issue.id,
                    issue.title,
                    issue.status,
                    issue.assignee,
                    issue.labels,
                    issue.parent != null ? issue.parent.id : null,
                    issue.updatedAt,
                    childCounts
            );
        }
    }

    /**
     * The row the issues list hands over: a summary row plus what grouping one
     * level of children adds to it.
     *
     * <p>A row is a root. Its {@code children} are that root's filter-matching
     * first-level children — capped at
     * {@link IssueService#MAX_EMBEDDED_CHILDREN}, oldest-created-first,
     * and light (a summary row carries no children of its own, which the
     * one-level rule keeps empty anyway). Its {@code matchesFilter} is false
     * exactly when the root got onto the page only as a container for matching
     * children — a filtered-out parent kept visible by a filtered-in child. And
     * {@code matchingChildCount} is how many children matched in total, so the
     * difference between it and the rows embedded is what an "...and N more"
     * link stands for.
     */
    public static class IssueListRowDto extends IssueSummaryDto {

        public final boolean matchesFilter;
        public final long matchingChildCount;
        public final List<IssueSummaryDto> children;

        @JsonCreator
        public IssueListRowDto(
                @JsonProperty("id") Long id,
                @JsonProperty("title") String title,
                @JsonProperty("status") String status,
                @JsonProperty("assignee") String assignee,
                @JsonProperty("labels") Set<String> labels,
                @JsonProperty("parentId") Long parentId,
                @JsonProperty("updatedAt") Instant updatedAt,
                @JsonProperty("childCounts") ChildCounts childCounts,
                @JsonProperty("matchesFilter") boolean matchesFilter,
                @JsonProperty("matchingChildCount") long matchingChildCount,
                @JsonProperty("children") List<IssueSummaryDto> children) {
            super(id, title, status, assignee, labels, parentId, updatedAt, childCounts);
            this.matchesFilter = matchesFilter;
            this.matchingChildCount = matchingChildCount;
            this.children = children != null ? children : List.of();
        }

        /** A row that already is a summary, plus the grouping its children bring. */
        public IssueListRowDto(IssueSummaryDto summary, boolean matchesFilter, long matchingChildCount, List<IssueSummaryDto> children) {
            super(summary.id, summary.title, summary.status, summary.assignee, summary.labels,
                    summary.parentId, summary.updatedAt, summary.childCounts);
            this.matchesFilter = matchesFilter;
            this.matchingChildCount = matchingChildCount;
            this.children = children != null ? children : List.of();
        }

        /**
         * Map the service's list rows — the one place a service row becomes a
         * list row, so every list surface (REST and MCP alike) shapes its output
         * identically.
         */
        public static List<IssueListRowDto> fromRows(List<IssueService.IssueListRow> rows) {
            return rows.stream()
                    .map(IssueListRowDto::fromRow)
                    .toList();
        }

        /**
         * Map one service row: the summary the row already was, plus the
         * grouping its children bring.
         */
        public static IssueListRowDto fromRow(IssueService.IssueListRow row) {
            List<IssueSummaryDto> children = row.children().embedded().stream()
                    .map(IssueSummaryDto::fromIssue)
                    .toList();
            return new IssueListRowDto(
                    IssueSummaryDto.fromIssue(row.issue(), row.childCounts()),
                    row.matchesFilter(),
                    row.children().matched(),
                    children);
        }
    }
}
