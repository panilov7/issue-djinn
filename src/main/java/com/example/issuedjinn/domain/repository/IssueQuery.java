package com.example.issuedjinn.domain.repository;

import com.example.issuedjinn.rest.api.dto.IssueSearchParams;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Query parameters for listing issues.
 * Internal model used by service and repository layers.
 */
public class IssueQuery {

    public String status;
    public Long parentId;
    public boolean flat;            // true = every match is its own row, nothing grouped
    public List<String> labels;
    public String assignee;         // exact match on assignee field
    public Boolean hasAssignee;     // true=assigned, false=unassigned, null=no filter
    public Boolean hasOpenDependency; // true=has open deps, false=no deps, null=no filter
    public String search;
    public int offset = 0;
    public int limit = 50;

    /**
     * The field parent-level rows are ordered by — the REST {@code sort} value,
     * createdAt by default; the whitelist and its 400 live on
     * {@link IssueOrder.Field#fromValue}.
     */
    public IssueOrder.Field sortField = IssueOrder.Field.CREATED_AT;

    /**
     * The direction parent-level rows run in — the REST {@code direction}
     * value. Defaults to DESC (newest-created-first).
     */
    public IssueOrder.Direction direction = IssueOrder.Direction.DESC;

    /**
     * The direction children are served in — the REST {@code child_direction}
     * value, wherever children appear (list embeds, {@code ?parent=N} rows,
     * detail children). Defaults to ASC (oldest-created-first). The parent
     * sort/direction never reach the children: a query that names a parent
     * lists that parent's children as their own rows, so only
     * {@link #childDirection} orders them.
     */
    public IssueOrder.Direction childDirection = IssueOrder.Direction.ASC;

    /**
     * An explicit order a caller pins, overriding {@link #defaultOrder()} — set
     * only where a surface is deliberately exempt from the global default. Today
     * that is one place: the frontier lists its actionable set oldest-first
     * whatever parent it filters by, so the "lowest number wins"
     * picking convention falls out of the list order directly.
     */
    public IssueOrder pinnedOrder;

    /**
     * The order a listing actually runs in: the pinned one when there is one,
     * the query's default otherwise (see {@link #defaultOrder()}).
     */
    public IssueOrder effectiveOrder() {
        return pinnedOrder != null ? pinnedOrder : defaultOrder();
    }

    /**
     * The default listing order this query asks for (the one ordering rule, see
     * {@link IssueOrder}): parent-level rows — roots, and the flat rows that do
     * not name a parent — in the REST sort+direction (newest-created-first by
     * default, see {@link #parentOrder()}), while a query that names a parent
     * asks for that parent's children as their own rows and gets them in the
     * REST child_direction (oldest-created-first by default, see
     * {@link #childOrder()}). A query that names a parent ignores its
     * sort/direction — those order parent rows, and these rows are children.
     */
    public IssueOrder defaultOrder() {
        return parentId != null ? childOrder() : parentOrder();
    }

    /** The order of parent-level rows: the REST {@code sort} + {@code direction}, newest-created-first by default. */
    public IssueOrder parentOrder() {
        return IssueOrder.of(sortField, direction);
    }

    /** The order children are served in: the REST {@code child_direction}, oldest-created-first by default. */
    public IssueOrder childOrder() {
        return IssueOrder.childOrder(childDirection);
    }

    /**
     * The child order for a caller that names only a child direction — the
     * detail endpoint's {@code child_direction} parameter. Absent
     * (null, or a blank value, the codebase's blank-means-absent rule) means
     * the default oldest-created-first; the whitelist rejects anything else
     * with the same 400 the listing's parameters get.
     */
    public static IssueOrder childOrderFromParam(String childDirection) {
        if (!notBlank(childDirection)) {
            return IssueOrder.OLDEST_CREATED_FIRST;
        }
        return IssueOrder.childOrder(IssueOrder.Direction.fromValue(childDirection));
    }

    public IssueQuery() {
    }

    /**
     * Convert from REST search params to internal query.
     */
    public static IssueQuery fromSearchParams(IssueSearchParams params) {
        IssueQuery query = new IssueQuery();
        query.status = params.status;
        query.parentId = params.parent;
        query.flat = Boolean.TRUE.equals(params.flat);
        query.labels = params.labels;
        query.assignee = params.assignee;
        query.hasAssignee = params.hasAssignee;
        query.hasOpenDependency = params.hasOpenDependency;
        query.search = params.search;
        // The sort whitelists reject an unrecognized value here, before any
        // query runs — an off-whitelist value is a client error (400) whatever
        // the listing mode, even on a ?parent=N listing that would otherwise
        // ignore the parent sort. A blank value means absent, the
        // codebase's blank-means-absent rule (labels, search): RESTEasy may
        // deliver a present-but-empty parameter, and it carries no sort to apply.
        if (notBlank(params.sort)) {
            query.sortField = IssueOrder.Field.fromValue(params.sort);
        }
        if (notBlank(params.direction)) {
            query.direction = IssueOrder.Direction.fromValue(params.direction);
        }
        if (notBlank(params.childDirection)) {
            query.childDirection = IssueOrder.Direction.fromValue(params.childDirection);
        }
        query.offset = params.offset != null ? params.offset : 0;
        query.limit = params.limit != null ? Math.min(params.limit, 200) : 50;
        return query;
    }

    /** Whether a REST parameter value carries something to apply — blank means absent. */
    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * The search term as it is matched, if there is one: surrounding whitespace
     * and an optional leading '#' removed.
     *
     * <p>Both halves of the search — the text match and the id match — work from
     * this one normalised form, so {@code "#36"}, {@code "36"}, {@code " 36 "}
     * and {@code " #36 "} all behave identically. The UI search box does not
     * trim, which is why the backend strips.
     */
    public Optional<String> normalizedSearch() {
        if (search == null) {
            return Optional.empty();
        }

        String term = search.strip();
        if (term.startsWith("#")) {
            term = term.substring(1);
        }
        return term.isEmpty() ? Optional.empty() : Optional.of(term);
    }

    /**
     * The labels to filter by, with blank values dropped.
     *
     * <p>A blank {@code label} value carries no label to match, so it filters
     * nothing — the same rule as a blank {@code search}. Dropping it (rather
     * than binding it and matching nothing) keeps a blank value from silently
     * narrowing a query.
     *
     * <p>Surrounding whitespace on a real label is kept: labels are opaque
     * strings (ADR-0001), so only a value with no non-whitespace character is
     * treated as absent.
     *
     * <p>This lives on the query rather than in the REST layer because MCP
     * callers pass a label list straight through. RESTEasy already drops a
     * truly empty {@code ?label=} parameter, but it delivers a whitespace-only
     * one, and MCP delivers blank and null entries verbatim — normalising here
     * is what makes one rule hold for every caller.
     */
    public List<String> normalizedLabels() {
        if (labels == null) {
            return List.of();
        }
        return labels.stream()
                .filter(label -> label != null && !label.isBlank())
                .toList();
    }

    /**
     * The issue id this search term names, if any.
     *
     * <p>A term names an id when its normalised form (see
     * {@link #normalizedSearch()}) is a positive integer: both {@code "36"} and
     * {@code "#36"} name issue 36. Everything else is a plain text search —
     * mixed terms like {@code "36 fix"} and non-positive numbers like
     * {@code "0"} or {@code "-3"} never produce an id match, and neither does
     * a number too large to be an id.
     *
     * <p>This lives on the query rather than in either transport layer so that
     * REST, MCP and the UI resolve a term identically.
     */
    public OptionalLong searchId() {
        String term = normalizedSearch().orElse("");
        if (!term.matches("\\d+")) {
            return OptionalLong.empty();
        }

        try {
            long id = Long.parseLong(term);
            return id > 0 ? OptionalLong.of(id) : OptionalLong.empty();
        } catch (NumberFormatException tooLargeToBeAnId) {
            return OptionalLong.empty();
        }
    }
}
