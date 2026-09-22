package com.example.issuedjinn.domain.service;

import com.example.issuedjinn.domain.entity.Comment;
import com.example.issuedjinn.domain.entity.Issue;
import com.example.issuedjinn.domain.repository.ChildCounts;
import com.example.issuedjinn.domain.repository.CommentRepository;
import com.example.issuedjinn.domain.repository.IssueOrder;
import com.example.issuedjinn.domain.repository.IssueQuery;
import com.example.issuedjinn.domain.repository.IssueRepository;
import com.example.issuedjinn.events.IssueChangeEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for Issue and Comment business logic.
 * All mutations and queries go through this service layer;
 * REST and MCP handlers delegate here — no business logic in the transport layer.
 */
@ApplicationScoped
public class IssueService {

    @Inject
    IssueRepository issueRepository;

    @Inject
    CommentRepository commentRepository;

    /**
     * Fires an {@link IssueChangeEvent} inside the mutating transaction. Both the
     * REST resource and the MCP tools inject this service, so every transport
     * emits through one choke point; the SSE transport picks the events up after
     * commit ({@code IssueChangeBroadcaster}). Field and lifecycle mutations fire
     * {@code issue_updated}, as do hierarchy changes (a reparent or unparent names
     * the child, old parent and new parent in its event), dependency changes
     * (an add or remove names the edge's two endpoints) and comments — a comment
     * is a coarse {@code issue_updated} on the commented issue.
     */
    @Inject
    Event<IssueChangeEvent> issueChangeEvents;

    @PersistenceContext
    EntityManager entityManager;

    // ── Custom error codes ────────────────────────────────────────────────

    /** JSON-RPC error code for cycle-detection rejection (server-defined, -32000 range). */
    public static final int CYCLE_DETECTED_CODE = -32003;

    /** JSON-RPC error code for hierarchy-depth rejection (server-defined, -32000 range). */
    public static final int HIERARCHY_DEPTH_EXCEEDED_CODE = -32004;

    /**
     * Maximum depth of the issue hierarchy. 1 means a root may have children but a
     * child may not (CONTEXT.md → Child). Raise it here to allow deeper chains;
     * the cap applies to the deepest issue a move would produce, not just to the
     * issue whose parent is being set.
     */
    public static final int MAX_HIERARCHY_DEPTH = 1;

    /** Safety limit for the recursive parent/dependency chain CTEs (ADR-0004). */
    private static final int MAX_CHAIN_DEPTH = 1000;

    /**
     * How many filter-matching first-level children one list row embeds under
     * its root. The rest of the matching children ride behind an "...and N more"
     * link to the root's details page, where every child is listed.
     */
    public static final int MAX_EMBEDDED_CHILDREN = 10;

    // ── Exceptions ────────────────────────────────────────────────────────

    /**
     * Exception thrown when a block operation would create a cycle.
     */
    public static class CycleDetectedException extends RuntimeException {
        public CycleDetectedException(String message) {
            super(message);
        }
    }

    /**
     * Exception thrown when a parent assignment would push an issue deeper than
     * {@link #MAX_HIERARCHY_DEPTH}.
     */
    public static class HierarchyDepthExceededException extends RuntimeException {
        public HierarchyDepthExceededException(String message) {
            super(message);
        }
    }

    /**
     * Exception thrown when an issue is not found.
     */
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }

    /**
     * Exception thrown when an issue is already claimed by a different agent.
     */
    public static class AlreadyClaimedException extends RuntimeException {
        public AlreadyClaimedException(String message) {
            super(message);
        }
    }

    /**
     * Exception thrown when an issue status transition is invalid.
     */
    public static class InvalidStatusTransitionException extends RuntimeException {
        public InvalidStatusTransitionException(String message) {
            super(message);
        }
    }

    /**
     * The requested parent change in an issue update. Three states because the
     * surfaces distinguish them differently: MCP cannot express "explicit null"
     * (absent and null both bind to null), while REST PATCH can — so a single
     * value type carries the caller's intent into one transaction.
     */
    public static final class ParentUpdate {

        private enum Kind { UNCHANGED, CLEAR, SET }

        private static final ParentUpdate UNCHANGED_INSTANCE = new ParentUpdate(Kind.UNCHANGED, null);

        private final Kind kind;
        private final Long parentId;

        private ParentUpdate(Kind kind, Long parentId) {
            this.kind = kind;
            this.parentId = parentId;
        }

        /** Leave the parent as it is. */
        public static ParentUpdate unchanged() {
            return UNCHANGED_INSTANCE;
        }

        /** Remove the parent (unparent). */
        public static ParentUpdate clear() {
            return new ParentUpdate(Kind.CLEAR, null);
        }

        /** Set or replace the parent. */
        public static ParentUpdate set(Long parentId) {
            return new ParentUpdate(Kind.SET, parentId);
        }

        /**
         * True when the request asks for a parent change (set or clear) — the
         * mutations whose event names the hierarchy ends, not just the child.
         */
        public boolean changesParent() {
            return kind != Kind.UNCHANGED;
        }

        public boolean isClear() {
            return kind == Kind.CLEAR;
        }

        /** The new parent id; only meaningful for a SET change. */
        public Long parentId() {
            return parentId;
        }
    }

    /**
     * The detail view of one issue: the issue itself plus the children its detail
     * payload embeds. Both are entities; the transport layers map them to DTOs.
     */
    public record IssueWithChildren(Issue issue, List<Issue> children) {
    }

    /**
     * The filter-matching first-level children one list row groups under its
     * root: the ones the row embeds, capped at {@link #MAX_EMBEDDED_CHILDREN}
     * in the query's child order (oldest-created-first by default), plus how
     * many matched in total — the difference between the two is what an
     * "...and N more" link stands for.
     */
    public record GroupedChildren(List<Issue> embedded, long matched) {

        /** The grouping of a root with no filter-matching children. */
        public static final GroupedChildren NONE = new GroupedChildren(List.of(), 0);
    }

    /**
     * One row of the issues list: the issue, the child counts its row embeds,
     * whether it matched the filters itself, and the matching children grouped
     * under it.
     *
     * <p>The counts belong to the row, not to the issue — they are page-local,
     * taken over the row's first-level children and never narrowed by the filters
     * that selected the row (see {@link ChildCounts}). {@code matchesFilter} is
     * false exactly for the roots that are on the page only as containers for
     * matching children — a filtered-out parent kept visible by a filtered-in
     * child — and the grouping of children is empty for the flat listing a
     * parent filter produces.
     */
    public record IssueListRow(Issue issue, ChildCounts childCounts, boolean matchesFilter, GroupedChildren children) {
    }

    // ── Create ────────────────────────────────────────────────────────────

    /**
     * Create a new issue.
     *
     * @param title       required, non-blank
     * @param description optional, defaults to ""
     * @param parentId    optional parent issue id
     * @param labels      optional initial labels
     * @return the created issue (persisted, with id)
     */
    @Transactional
    public Issue createIssue(String title, String description, Long parentId, Set<String> labels) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Title is required");
        }

        Issue issue = new Issue();
        issue.title = title.trim();
        issue.description = description != null ? description : "";
        issue.status = "open";

        if (parentId != null) {
            applyParent(issue, parentId);
        }

        if (labels != null && !labels.isEmpty()) {
            issue.labels.addAll(labels);
        }

        issueRepository.persist(issue);
        issueChangeEvents.fire(IssueChangeEvent.created(issue.id, parentId));
        return reinitialize(issue);
    }

    // ── Read ──────────────────────────────────────────────────────────────

    /**
     * Get an issue by ID, throwing NotFoundException if absent.
     * Initializes lazy collections to avoid LazyInitializationException.
     */
    @Transactional
    public Issue getIssue(Long id) {
        Issue issue = issueRepository.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Issue not found: " + id));
        initializeCollections(issue);
        return issue;
    }

    /**
     * Get an issue together with the children its detail payload embeds — every
     * status, uncapped, oldest-created-first. The REST detail endpoint's
     * {@code child_direction} rides in through the overload below; the MCP
     * tools pin the oldest-created-first child order.
     */
    @Transactional
    public IssueWithChildren getIssueWithChildren(Long id) {
        return getIssueWithChildren(id, IssueOrder.OLDEST_CREATED_FIRST);
    }

    /** Same detail view, with the children served in the given child order. */
    @Transactional
    public IssueWithChildren getIssueWithChildren(Long id, IssueOrder childOrder) {
        Issue issue = getIssue(id);
        return new IssueWithChildren(issue, childrenOf(issue, childOrder));
    }

    /**
     * List issues with optional filters, parent-level rows in the query's
     * parent order and children in its child order (the one ordering rule, see
     * {@link IssueOrder}; the REST {@code sort}/{@code direction}/
     * {@code child_direction} parameters drive both, defaulting to
     * newest-created-first parents and oldest-created-first children).
     *
     * <p>A query that asks for a flat listing — it names a parent (how the
     * frontier of one parent is listed), or it sets {@link IssueQuery#flat} (how a
     * picker offers every issue) — gets every match as its own row. Any other
     * query asks for the tracker's root view and gets its rows grouped: one row
     * per root, each with its filter-matching first-level children riding along
     * under it, and {@link IssueListRow#matchesFilter} telling a container root
     * from a matching one.
     *
     * @param query query parameters
     * @return one row per listed issue — roots for the grouped listing, every
     *         match for a flat one — in the query's effective order
     */
    @Transactional
    public List<IssueListRow> listIssues(IssueQuery query) {
        return query.flat || query.parentId != null ? flatRows(query) : groupedRows(query);
    }

    /**
     * One row per matching issue: the flat listing a parent filter asks for or a
     * {@code flat} query opts into, where every match is its own row and nothing
     * is grouped under it.
     */
    private List<IssueListRow> flatRows(IssueQuery query) {
        List<Issue> issues = issueRepository.findWithFilters(query);
        issues.forEach(this::initializeLabels);

        Map<Long, ChildCounts> childCounts = issueRepository.countChildrenByParent(idsOf(issues));
        return issues.stream()
                .map(issue -> new IssueListRow(issue,
                        childCounts.getOrDefault(issue.id, ChildCounts.NONE),
                        true,
                        GroupedChildren.NONE))
                .toList();
    }

    /**
     * One row per root on the page, its filter-matching first-level children
     * riding along under it.
     *
     * <p>Children take no page slot of their own — pagination counts roots, so a
     * page of 50 is 50 roots however many children ride along — and a child never
     * appears as its own top-level row, since only roots are selected.
     */
    private List<IssueListRow> groupedRows(IssueQuery query) {
        List<Issue> roots = issueRepository.findRootPage(query);
        roots.forEach(this::initializeLabels);

        List<Long> rootIds = idsOf(roots);
        Map<Long, ChildCounts> childCounts = issueRepository.countChildrenByParent(rootIds);
        Map<Long, GroupedChildren> groupedChildren = groupedChildrenOf(rootIds, query);
        Set<Long> matchingIds = issueRepository.idsMatchingFilters(rootIds, query);

        return roots.stream()
                .map(root -> new IssueListRow(root,
                        childCounts.getOrDefault(root.id, ChildCounts.NONE),
                        matchingIds.contains(root.id),
                        groupedChildren.getOrDefault(root.id, GroupedChildren.NONE)))
                .toList();
    }

    /**
     * The filter-matching children of the given roots, keyed by root id.
     */
    private Map<Long, GroupedChildren> groupedChildrenOf(List<Long> rootIds, IssueQuery query) {
        Map<Long, List<Issue>> matching = issueRepository.findMatchingChildren(rootIds, query).stream()
                .collect(Collectors.groupingBy(child -> child.parent.id));
        return rootIds.stream()
                .collect(Collectors.toMap(rootId -> rootId,
                        rootId -> cappedGrouping(matching.getOrDefault(rootId, List.of()))));
    }

    /**
     * The grouping one row carries: the first {@link #MAX_EMBEDDED_CHILDREN}
     * of the children that matched ride along — the children arrive in the
     * query's child order, so the cap keeps the top of the detail page's list —
     * and the count records how many matched in total, the overflow the cap
     * hid.
     */
    private GroupedChildren cappedGrouping(List<Issue> matchingChildren) {
        List<Issue> embedded = matchingChildren.stream()
                .limit(MAX_EMBEDDED_CHILDREN)
                .toList();
        embedded.forEach(this::initializeLabels);
        return new GroupedChildren(embedded, matchingChildren.size());
    }

    /**
     * List issues that this issue depends on (prerequisites — issues that must be resolved first).
     */
    @Transactional
    public List<Issue> listDependencies(Long id) {
        Issue issue = getIssue(id);
        // Force initialization of the dependencies collection
        List<Issue> result = List.copyOf(issue.dependencies);
        return result;
    }

    /**
     * List issues that depend on this issue (waiters — issues that can't start until this one is resolved).
     */
    @Transactional
    public List<Issue> listDependents(Long id) {
        Issue issue = getIssue(id);
        // Force initialization of the dependents collection
        List<Issue> result = List.copyOf(issue.dependents);
        return result;
    }

    /**
     * List children of the given parent issue, oldest-created-first — the fixed
     * child order the MCP child-serving tools pin.
     */
    @Transactional
    public List<Issue> listChildren(Long parentId) {
        return childrenOf(getIssue(parentId), IssueOrder.OLDEST_CREATED_FIRST);
    }

    /**
     * The children of an issue, in the given child order — the order the
     * detail payload embeds them in ({@link IssueRepository#findChildrenOf}).
     */
    private List<Issue> childrenOf(Issue parent, IssueOrder childOrder) {
        List<Issue> children = issueRepository.findChildrenOf(parent.id, childOrder);
        children.forEach(this::initializeCollections);
        return children;
    }

    /**
     * List frontier issues: open, no open dependencies, no assignee.
     * Optionally filtered by parent issue id. The same rows the issues list
     * returns, counts included — a frontier parent's counts are the work sitting
     * under it.
     *
     * <p>The frontier is the one surface exempt from the global default order
     * (see {@link IssueOrder}): it is pinned oldest-created-first whatever parent
     * it lists, so the picking convention — take the first row, "lowest number
     * wins" — falls out of the list order directly.
     */
    @Transactional
    public List<IssueListRow> listFrontier(Long parentId) {
        // status=open, hasOpenDependency=false (no open dependencies), hasAssignee=false (unassigned)
        IssueQuery query = new IssueQuery();
        query.status = "open";
        query.parentId = parentId;
        // The frontier is the flat set of actionable issues (CONTEXT.md → frontier
        // query), not a view of them: a frontier child is a row of its own, so an
        // agent picks work straight off the list instead of digging into groups —
        // and nothing under a root is hidden by the embedded-children cap.
        query.flat = true;
        query.hasAssignee = false;
        query.hasOpenDependency = false;
        query.pinnedOrder = IssueOrder.OLDEST_CREATED_FIRST;
        query.offset = 0;
        query.limit = 200;
        return listIssues(query);
    }

    // ── Update ────────────────────────────────────────────────────────────

    /**
     * Update an issue's mutable fields. Null parameters mean "don't change".
     * Labels is a full-array replacement: null = don't change, empty = remove all.
     * The parent change is applied in the same transaction, so a rejected parent
     * (missing or cyclic) rejects the whole update.
     *
     * @param parentUpdate the requested parent change; {@link ParentUpdate#unchanged()}
     *                     leaves the parent as it is
     */
    @Transactional
    public Issue updateIssue(Long id, String title, String description,
                              String assignee, Set<String> labels, ParentUpdate parentUpdate) {
        Issue issue = getIssue(id);
        boolean parentChangeRequested = parentUpdate != null && parentUpdate.changesParent();
        Long oldParentId = parentChangeRequested ? parentIdOf(issue) : null;

        if (title != null && !title.isBlank()) {
            issue.title = title;
        }
        if (description != null) {
            issue.description = description;
        }
        if (assignee != null) {
            issue.assignee = assignee;
        }
        if (labels != null) {
            issue.labels.clear();
            issue.labels.addAll(labels);
        }
        applyParentChange(issue, parentUpdate);

        issueRepository.persist(issue);
        // A request that names a parent change is a hierarchy mutation: its event
        // names the hierarchy ends, not just the child.
        issueChangeEvents.fire(parentChangeRequested
                ? IssueChangeEvent.reparented(issue.id, oldParentId, parentIdOf(issue))
                : IssueChangeEvent.updated(issue.id));
        return reinitialize(issue);
    }

    /**
     * Change an issue's parent. Null newParentId removes the parent (unparent).
     * The new parent may be open or closed. Rejects moves that would create a
     * parent chain cycle (including self-parenting).
     *
     * @param id          the issue to reparent
     * @param newParentId the new parent issue id, or null to unparent
     * @return the updated issue
     * @throws NotFoundException      if the issue or the new parent does not exist
     * @throws CycleDetectedException if the move would create a cycle in the parent chain
     */
    @Transactional
    public Issue reparent(Long id, Long newParentId) {
        Issue issue = getIssue(id);
        Long oldParentId = parentIdOf(issue);

        if (newParentId != null) {
            applyParent(issue, newParentId);
        } else {
            issue.parent = null;
        }

        issueRepository.persist(issue);
        issueChangeEvents.fire(IssueChangeEvent.reparented(issue.id, oldParentId, newParentId));
        return reinitialize(issue);
    }

    /**
     * Close an issue. Creates a closing comment and sets status to "closed".
     *
     * @param id      the issue to close
     * @param comment the required closing comment
     * @param author  the author of the closing comment (required, non-blank)
     * @return the updated issue
     * @throws IllegalArgumentException     if comment or author is blank
     * @throws NotFoundException          if issue not found
     * @throws InvalidStatusTransitionException if issue is already closed
     */
    @Transactional
    public Issue closeIssue(Long id, String comment, String author) {
        if (comment == null || comment.isBlank()) {
            throw new IllegalArgumentException("Closing comment is required");
        }
        if (author == null || author.isBlank()) {
            throw new IllegalArgumentException("Author is required");
        }

        Issue issue = getIssue(id);

        if ("closed".equals(issue.status)) {
            throw new InvalidStatusTransitionException("Issue is already closed");
        }

        Comment closeComment = new Comment();
        closeComment.issue = issue;
        closeComment.author = author.trim();
        closeComment.body = comment;
        commentRepository.persist(closeComment);
        issue.comments.add(closeComment);

        issue.status = "closed";
        issueRepository.persist(issue);
        issueChangeEvents.fire(IssueChangeEvent.updated(issue.id));
        return reinitialize(issue);
    }

    /**
     * Reopen a closed issue. Creates a reopening comment and sets status to "open".
     *
     * @param id      the issue to reopen
     * @param comment the required reopening comment
     * @param author  the author of the reopening comment (required, non-blank)
     * @return the updated issue
     * @throws IllegalArgumentException     if comment or author is blank
     * @throws NotFoundException          if issue not found
     * @throws InvalidStatusTransitionException if issue is already open
     */
    @Transactional
    public Issue reopenIssue(Long id, String comment, String author) {
        if (comment == null || comment.isBlank()) {
            throw new IllegalArgumentException("Reopening comment is required");
        }
        if (author == null || author.isBlank()) {
            throw new IllegalArgumentException("Author is required");
        }

        Issue issue = getIssue(id);

        if ("open".equals(issue.status)) {
            throw new InvalidStatusTransitionException("Issue is already open");
        }

        Comment reopenComment = new Comment();
        reopenComment.issue = issue;
        reopenComment.author = author.trim();
        reopenComment.body = comment;
        commentRepository.persist(reopenComment);
        issue.comments.add(reopenComment);

        issue.status = "open";
        issueRepository.persist(issue);
        issueChangeEvents.fire(IssueChangeEvent.updated(issue.id));
        return reinitialize(issue);
    }

    /**
     * Unassign an issue (set assignee to null).
     */
    @Transactional
    public Issue unassignIssue(Long id) {
        Issue issue = getIssue(id);
        issue.assignee = null;
        issueRepository.persist(issue);
        issueChangeEvents.fire(IssueChangeEvent.updated(issue.id));
        return reinitialize(issue);
    }

    /**
     * Claim an issue for an agent. Fails if already claimed by a different agent.
     *
     * @param id         the issue to claim
     * @param agentName  the claiming agent's name (required, non-blank)
     * @return the updated issue
     * @throws IllegalArgumentException  if agentName is blank
     * @throws AlreadyClaimedException   if issue is already claimed by someone else
     */
    @Transactional
    public Issue claimIssue(Long id, String agentName) {
        if (agentName == null || agentName.isBlank()) {
            throw new IllegalArgumentException("Agent name is required");
        }

        Issue issue = getIssue(id);

        if (issue.assignee != null && !issue.assignee.equals(agentName)) {
            throw new AlreadyClaimedException(
                    "Issue already claimed by: " + issue.assignee);
        }

        issue.assignee = agentName;
        issueRepository.persist(issue);
        issueChangeEvents.fire(IssueChangeEvent.updated(issue.id));
        return reinitialize(issue);
    }

    // ── Dependency relationships ────────────────────────────────────────

    /**
     * Add a dependency relationship where dependentId depends on dependencyId.
     * Rejects with CycleDetectedException if the relationship would create a cycle.
     *
     * @param dependentId   the issue that depends (the waiting issue)
     * @param dependencyId  the issue being depended on (the prerequisite)
     * @return the updated dependent issue
     */
    @Transactional
    public Issue addDependency(Long dependentId, Long dependencyId) {
        Issue dependent = issueRepository.findByIdOptional(dependentId).orElseThrow(() ->
                new NotFoundException("Dependent issue not found: " + dependentId));

        Issue dependency = issueRepository.findByIdOptional(dependencyId).orElseThrow(() ->
                new NotFoundException("Dependency issue not found: " + dependencyId));

        // Check for cycles FIRST using the native query
        // This must happen BEFORE persisting the relationship
        if (wouldCreateCycle(dependentId, dependencyId)) {
            throw new CycleDetectedException(
                    String.format("Cannot add dependency: would create a cycle between issue %d and issue %d", dependentId, dependencyId));
        }

        // Add the dependency relationship
        boolean edgeAdded = dependent.addDependency(dependency);

        // Touch the issue to trigger updated_at bump for related-entity inserts
        dependent.persist();

        // One event names both endpoints, so a detail page of either end refetches;
        // a re-add of an edge that already existed commits nothing and emits nothing.
        if (edgeAdded) {
            issueChangeEvents.fire(IssueChangeEvent.dependencyChanged(dependentId, dependencyId));
        }

        return dependent;
    }

    /**
     * Remove a dependency relationship where dependentId depends on dependencyId.
     *
     * @param dependentId   the issue that no longer depends
     * @param dependencyId  the issue that is no longer a dependency
     * @return the updated dependent issue
     */
    @Transactional
    public Issue removeDependency(Long dependentId, Long dependencyId) {
        Issue dependent = issueRepository.findByIdOptional(dependentId).orElseThrow(() ->
                new NotFoundException("Dependent issue not found: " + dependentId));

        Issue dependency = issueRepository.findByIdOptional(dependencyId).orElseThrow(() ->
                new NotFoundException("Dependency issue not found: " + dependencyId));

        boolean edgeRemoved = dependent.removeDependency(dependency);

        // Touch the issue to trigger updated_at bump
        dependent.persist();

        // A remove of an edge that was not there commits nothing and emits nothing.
        if (edgeRemoved) {
            issueChangeEvents.fire(IssueChangeEvent.dependencyChanged(dependentId, dependencyId));
        }

        return dependent;
    }

    // ── Comments ─────────────────────────────────────────────────────────

    /**
     * Add a comment to an issue.
     *
     * @param issueId the issue to comment on
     * @param author  the comment author (required, non-blank)
     * @param body    the comment body (defaults to "" if null)
     * @return the created comment
     */
    @Transactional
    public Comment addComment(Long issueId, String author, String body) {
        if (author == null || author.isBlank()) {
            throw new IllegalArgumentException("Author is required");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Comment body is required");
        }

        Issue issue = getIssue(issueId);

        Comment comment = new Comment();
        comment.issue = issue;
        comment.author = author.trim();
        comment.body = body.trim();
        commentRepository.persist(comment);
        issue.comments.add(comment);

        // Touch the issue to trigger updated_at bump
        issue.persist();

        // A comment is a coarse issue_updated on the commented issue — the detail
        // page's reaction is refetch, which covers comments too.
        issueChangeEvents.fire(IssueChangeEvent.updated(issue.id));

        return comment;
    }

    /**
     * Get all comments for an issue, ordered by createdAt ASC.
     */
    @Transactional
    public List<Comment> getComments(Long issueId) {
        Issue issue = getIssue(issueId);
        return issue.comments.stream()
                .sorted((c1, c2) -> c1.createdAt.compareTo(c2.createdAt))
                .toList();
    }

    // ── Private helpers ───────────────────────────────────────────────────

    /**
     * Apply the requested parent change to the issue. Null or unchanged leaves the
     * parent alone; clear removes it; set validates and assigns the new parent.
     */
    private void applyParentChange(Issue issue, ParentUpdate parentUpdate) {
        if (parentUpdate == null || !parentUpdate.changesParent()) {
            return;
        }
        if (parentUpdate.isClear()) {
            issue.parent = null;
            return;
        }
        applyParent(issue, parentUpdate.parentId());
    }

    /**
     * Flush pending changes and initialize the issue's lazy collections, so the
     * entity is safe to read once the transaction ends.
     */
    private Issue reinitialize(Issue issue) {
        entityManager.flush();
        initializeCollections(issue);
        return issue;
    }

    /** Force-initialize an issue's lazy collections inside its open transaction. */
    private void initializeCollections(Issue issue) {
        issue.dependencies.size();
        issue.dependents.size();
        issue.comments.size();
    }

    /**
     * Force-initialize an issue's labels, the one lazy field a list row reads.
     */
    private void initializeLabels(Issue issue) {
        issue.labels.size();
    }

    /** The ids of the listed issues — what the child counts are keyed by. */
    private static List<Long> idsOf(List<Issue> issues) {
        return issues.stream().map(issue -> issue.id).toList();
    }

    /** The issue's parent id, or null for a root — what a hierarchy event names. */
    private static Long parentIdOf(Issue issue) {
        return issue.parent == null ? null : issue.parent.id;
    }

    /**
     * Validate and set an issue's parent: the parent must exist (open or closed),
     * the move must not create a cycle in the parent chain (ADR-0004), and it must
     * not grow the hierarchy past {@link #MAX_HIERARCHY_DEPTH}.
     */
    private void applyParent(Issue issue, Long newParentId) {
        Issue parent = issueRepository.findByIdOptional(newParentId)
                .orElseThrow(() -> new NotFoundException("Parent issue not found: " + newParentId));

        rejectIfWouldCreateParentCycle(issue, newParentId);
        rejectIfWouldExceedHierarchyDepth(issue, newParentId);

        issue.parent = parent;
    }

    /**
     * Reject a move that would make an issue its own ancestor. A new (unpersisted)
     * issue has no ancestors yet, so it cannot close a cycle.
     */
    private void rejectIfWouldCreateParentCycle(Issue issue, Long newParentId) {
        if (issue.id != null && wouldCreateParentCycle(issue.id, newParentId)) {
            throw new CycleDetectedException(String.format(
                    "Cannot reparent issue %d under issue %d: would create a cycle", issue.id, newParentId));
        }
    }

    /**
     * Reject a move that would push an issue deeper than {@link #MAX_HIERARCHY_DEPTH}.
     * The issue being re-parented lands one level under the new parent and everything
     * already under it moves down with it, so the deepest point of the move is the new
     * parent's depth, plus one for the issue, plus the height of its subtree.
     *
     * A move that leaves every issue at the depth it already had is allowed even when
     * that depth is past the cap: pre-existing deeper chains are legacy
     * (CONTEXT.md → Child) — they may stay put or shallow out, but never grow.
     */
    private void rejectIfWouldExceedHierarchyDepth(Issue issue, Long newParentId) {
        int depthAfterMove = depthOf(newParentId) + 1;
        boolean deepensTheSubtree = issue.id == null || depthAfterMove > depthOf(issue.id);

        if (deepensTheSubtree && depthAfterMove + subtreeHeight(issue.id) > MAX_HIERARCHY_DEPTH) {
            throw new HierarchyDepthExceededException(String.format(
                    "Cannot set parent to issue %d: max hierarchy depth is %d", newParentId, MAX_HIERARCHY_DEPTH));
        }
    }

    /**
     * Checks if making childId a child of parentId would create a cycle, i.e. whether
     * childId is already an ancestor of parentId (self-parenting is the trivial case).
     * Uses a recursive CTE to walk the parent chain in the issues table directly.
     *
     * @param childId  the issue that would become the child
     * @param parentId the issue that would become the parent
     * @return true if a cycle would be created, false otherwise
     */
    private boolean wouldCreateParentCycle(Long childId, Long parentId) {
        if (childId.equals(parentId)) {
            return true;
        }

        // Recursive CTE to check if childId is an ancestor of parentId.
        // The issues table links parents via parent_id ("issue.parent_id" points upward).
        //
        // To check if childId is an ancestor of parentId, we:
        // 1. Start from parentId's own parent
        // 2. Follow each ancestor's parent upward
        // 3. If childId appears in that chain, making it the parent of parentId would cycle
        String sql = """
            WITH RECURSIVE parent_chain(issue_id, depth) AS (
                -- Base case: the potential parent's own parent
                SELECT parent_id, 1 FROM issues WHERE id = :startId AND parent_id IS NOT NULL

                UNION ALL

                -- Recursive case: the parent of each ancestor in the chain
                SELECT i.parent_id, pc.depth + 1
                FROM issues i
                INNER JOIN parent_chain pc ON i.id = pc.issue_id
                WHERE pc.depth < :maxDepth  -- Safety limit
                  AND i.parent_id IS NOT NULL
            )
            SELECT 1 FROM parent_chain WHERE issue_id = :childId LIMIT 1;
            """;

        Object result = entityManager.createNativeQuery(sql)
                .setParameter("startId", parentId)  // Start from the potential parent
                .setParameter("childId", childId)   // Check if the child is already in its ancestor chain
                .setParameter("maxDepth", MAX_CHAIN_DEPTH)
                .getSingleResultOrNull();

        return result != null;
    }

    /**
     * Depth of an issue in the hierarchy: the number of ancestors above it.
     * A root has depth 0, its children 1, and so on. Walks the parent chain
     * with a recursive CTE, mirroring {@link #wouldCreateParentCycle}.
     *
     * @param issueId the issue to measure
     * @return the issue's depth, 0 for a root
     */
    private int depthOf(Long issueId) {
        String sql = """
            WITH RECURSIVE parent_chain(depth, issue_id) AS (
                -- Base case: the issue's own parent sits one level up
                SELECT 1, parent_id FROM issues WHERE id = :startId AND parent_id IS NOT NULL

                UNION ALL

                -- Recursive case: the parent of each ancestor in the chain
                SELECT pc.depth + 1, i.parent_id
                FROM issues i
                INNER JOIN parent_chain pc ON i.id = pc.issue_id
                WHERE pc.depth < :maxDepth  -- Safety limit
                  AND i.parent_id IS NOT NULL
            )
            SELECT MAX(depth) FROM parent_chain;
            """;

        Object deepest = entityManager.createNativeQuery(sql)
                .setParameter("startId", issueId)
                .setParameter("maxDepth", MAX_CHAIN_DEPTH)
                .getSingleResultOrNull();

        return deepest == null ? 0 : ((Number) deepest).intValue();
    }

    /**
     * Height of the subtree rooted at the issue: 0 for a leaf, 1 for an issue
     * with children, and so on. Walks the children down from the issue with a
     * recursive CTE. A new (unpersisted) issue has no children yet.
     *
     * @param issueId the issue to measure, null for an issue that is not persisted yet
     * @return the subtree height, 0 for a leaf or an unpersisted issue
     */
    private int subtreeHeight(Long issueId) {
        if (issueId == null) {
            return 0;
        }

        String sql = """
            WITH RECURSIVE subtree(depth, issue_id) AS (
                -- Base case: the issue is the root of its own subtree
                SELECT 0, id FROM issues WHERE id = :startId

                UNION ALL

                -- Recursive case: each child sits one level below its parent
                SELECT s.depth + 1, c.id
                FROM issues c
                INNER JOIN subtree s ON c.parent_id = s.issue_id
                WHERE s.depth < :maxDepth  -- Safety limit
            )
            SELECT MAX(depth) FROM subtree;
            """;

        Object deepest = entityManager.createNativeQuery(sql)
                .setParameter("startId", issueId)
                .setParameter("maxDepth", MAX_CHAIN_DEPTH)
                .getSingleResultOrNull();

        return deepest == null ? 0 : ((Number) deepest).intValue();
    }

    /**
     * Checks if adding a dependency from dependentId to dependencyId would create a cycle.
     * Uses a recursive CTE to query the issue_dependencies table directly.
     *
     * @param dependentId   the issue that would depend
     * @param dependencyId  the issue that would be depended on
     * @return true if a cycle would be created, false otherwise
     */
    private boolean wouldCreateCycle(Long dependentId, Long dependencyId) {
        // Check for self-reference
        if (dependentId.equals(dependencyId)) {
            return true;
        }

        // Recursive CTE to check if dependentId transitively depends on dependencyId.
        // If dependentId already depends on dependencyId (transitively), then adding
        // "dependentId depends on dependencyId" would create a cycle.
        //
        // The issue_dependencies table has: (dependent_id, dependency_id)
        // meaning "dependent_id depends on dependency_id"
        //
        // To check if dependentId depends on dependencyId, we:
        // 1. Start from dependencyId and follow what IT depends on
        // 2. If we find dependentId in that chain, then dependentId depends on dependencyId
        //    (transitively), so adding "dependentId depends on dependencyId" would cycle
        String sql = """
            WITH RECURSIVE dependency_chain(dependency_id, depth) AS (
                -- Base case: direct dependencies of the potential dependency
                SELECT dependency_id, 1 FROM issue_dependencies WHERE dependent_id = :startId
                
                UNION ALL
                
                -- Recursive case: dependencies of each dependency in the chain
                SELECT id.dependency_id, dc.depth + 1
                FROM issue_dependencies id
                INNER JOIN dependency_chain dc ON id.dependent_id = dc.dependency_id
                WHERE dc.depth < :maxDepth  -- Safety limit
            )
            SELECT 1 FROM dependency_chain WHERE dependency_id = :targetId LIMIT 1;
            """;

        Object result = entityManager.createNativeQuery(sql)
                .setParameter("startId", dependencyId)  // Start from the potential dependency
                .setParameter("targetId", dependentId)  // Check if the dependent is in its chain
                .setParameter("maxDepth", MAX_CHAIN_DEPTH)
                .getSingleResultOrNull();

        return result != null;
    }

    /**
     * Update the status of an issue.
     *
     * @param issueId the issue to update
     * @param status  the new status ('open' or 'closed')
     * @return the updated issue
     */
    @Transactional
    public Issue updateStatus(Long issueId, String status) {
        Issue issue = issueRepository.findByIdOptional(issueId).orElseThrow(() ->
                new NotFoundException("Issue not found: " + issueId));

        if (!"open".equals(status) && !"closed".equals(status)) {
            throw new IllegalArgumentException("Invalid status: " + status + ". Must be 'open' or 'closed'");
        }

        issue.status = status;
        issue.persist();
        issueChangeEvents.fire(IssueChangeEvent.updated(issue.id));
        return reinitialize(issue);
    }
}
