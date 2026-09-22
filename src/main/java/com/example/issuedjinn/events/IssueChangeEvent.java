package com.example.issuedjinn.events;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A change to one or more issues, fired by {@code IssueService} inside the mutating
 * transaction. Travels over CDI events: the domain service stays decoupled from the
 * SSE transport, and the observer that reaches the wire fires only after the
 * transaction committed — a rolled-back mutation emits nothing.
 *
 * <p>Events carry no entity bodies — the UI's reaction is "refetch", so the payload
 * lists only the mutated issue and the direct counterparts the mutation handler
 * already knows.
 *
 * @param type     what happened
 * @param issueIds the mutated issue first, then its counterparts (e.g. the parent on a create)
 * @param at       when the change happened
 */
public record IssueChangeEvent(Type type, List<Long> issueIds, Instant at) {

    /** The change types the wire protocol carries. */
    public enum Type {
        ISSUE_CREATED("issue_created"),
        ISSUE_UPDATED("issue_updated"),
        ISSUE_DELETED("issue_deleted"); // reserved: no delete mutation exists yet

        private final String wireName;

        Type(String wireName) {
            this.wireName = wireName;
        }

        /** The SSE event name and payload {@code type} value this type goes on the wire as. */
        public String wireName() {
            return wireName;
        }
    }

    /** A create with its (optional) parent: {@code issueIds = [newId, parentId?]} */
    public static IssueChangeEvent created(long newId, Long parentId) {
        List<Long> issueIds = parentId == null ? List.of(newId) : List.of(newId, parentId);
        return new IssueChangeEvent(Type.ISSUE_CREATED, issueIds, Instant.now());
    }

    /**
     * An update to one issue's fields or lifecycle state (title, description,
     * labels, assignee, status): {@code issueIds = [id]}
     */
    public static IssueChangeEvent updated(long id) {
        return new IssueChangeEvent(Type.ISSUE_UPDATED, List.of(id), Instant.now());
    }

    /**
     * A reparent or unparent of one issue: {@code issueIds = [childId, oldParentId?,
     * newParentId?]} — the child first, then the old parent and the new parent when
     * each exists, so a detail page of any of the three recognizes the change. The
     * same id twice (setting the parent an issue already has) is listed once.
     */
    public static IssueChangeEvent reparented(long childId, Long oldParentId, Long newParentId) {
        List<Long> issueIds = Stream.of(childId, oldParentId, newParentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return new IssueChangeEvent(Type.ISSUE_UPDATED, issueIds, Instant.now());
    }

    /**
     * An added or removed dependency edge: {@code issueIds = [dependentId,
     * dependencyId]} — the issue the request addressed first, then its direct
     * counterpart, so a detail page of either end recognizes the change. The
     * two are never the same id (a self-edge is a cycle, rejected before this
     * event fires), and only the edge's two endpoints are named — the backend
     * computes no transitive dependents.
     */
    public static IssueChangeEvent dependencyChanged(long dependentId, long dependencyId) {
        return new IssueChangeEvent(Type.ISSUE_UPDATED, List.of(dependentId, dependencyId), Instant.now());
    }
}
