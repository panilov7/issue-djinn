package com.example.issuedjinn.mcp;

import com.example.issuedjinn.domain.entity.Comment;
import com.example.issuedjinn.domain.entity.Issue;
import com.example.issuedjinn.domain.repository.IssueQuery;
import com.example.issuedjinn.domain.service.IssueService;
import com.example.issuedjinn.domain.service.IssueService.AlreadyClaimedException;
import com.example.issuedjinn.domain.service.IssueService.CycleDetectedException;
import com.example.issuedjinn.domain.service.IssueService.HierarchyDepthExceededException;
import com.example.issuedjinn.domain.service.IssueService.InvalidStatusTransitionException;
import com.example.issuedjinn.domain.service.IssueService.NotFoundException;
import com.example.issuedjinn.dto.CommentDto;
import com.example.issuedjinn.dto.IssueDto;
import com.example.issuedjinn.dto.IssueListDto;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolGuardrails;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;

/**
 * MCP tools for issue-djinn.
 * All 16 tools delegate to {@link IssueService} — no business logic here.
 * Error handling uses McpException with appropriate JSON-RPC error codes:
 * - INVALID_PARAMS (-32602) for bad input, including a missing issue id
 * - CYCLE_DETECTED_CODE (-32003) for cycle prevention
 * - HIERARCHY_DEPTH_EXCEEDED_CODE (-32004) for the one-level hierarchy cap
 * - INTERNAL_ERROR (-32603) for unexpected errors
 * Every tool declares {@link UnknownToolArgsGuardrail} so unknown arguments fail
 * loudly instead of being silently dropped — a new tool must too.
 */
@Singleton
public class IssueMcpTools {

    /**
     * Naming-convention note appended to every tool description: agents
     * see it on connect, in the same context as the argument names themselves.
     * A compile-time constant so it can be concatenated into annotation values.
     */
    private static final String ARG_NAMING_NOTE =
            " All arguments are snake_case; pass them exactly as advertised.";

    @Inject
    IssueService issueService;

    // ── 1. list_issues ─────────────────────────────────────────────────────

    @ToolGuardrails(input = UnknownToolArgsGuardrail.class)
    @Tool(name = "list_issues", description = "List issues in the local tracker, optionally filtered by status, parent issue (parent_id), labels, assignee, dependency status, or text search. Rows are grouped one level deep: each row is a root, embeds childCounts (the open/closed/total counts of its first-level children, taken over all of them regardless of the filters) and its filter-matching first-level children — capped at " + IssueService.MAX_EMBEDDED_CHILDREN + ". A root that misses the filters but has a matching child is still listed, flagged matchesFilter=false; a query naming a parent lists that parent's children flat instead. Roots are newest-created-first (createdAt desc, id desc); the children a row embeds are oldest-created-first (createdAt asc, id asc), the cap keeping the oldest and hiding the newest. has_assignee filters by assignee presence (true = claimed by anyone, false = unclaimed, absent = no filter); a non-empty assignee takes precedence over it." + ARG_NAMING_NOTE)
    public List<IssueListDto.IssueListRowDto> listIssues(
            @ToolArg(name = "status", description = "Filter by status: 'open' or 'closed'. Omit to list all.", required = false) String status,
            @ToolArg(name = "parent_id", description = "Filter by parent issue ID: lists that parent's child issues.", required = false) Long parentId,
            @ToolArg(name = "labels", description = "Filter by labels (AND semantics — issue must have ALL listed labels).", required = false) List<String> labels,
            @ToolArg(name = "assignee", description = "Filter by assignee name. A non-empty assignee takes precedence over has_assignee. Use empty string for unassigned issues.", required = false) String assignee,
            @ToolArg(name = "has_assignee", description = "Filter by assignee presence: true = claimed by anyone, false = unclaimed. Omit for no assignee filter. Ignored when a non-empty assignee is given.", required = false) Boolean hasAssignee,
            @ToolArg(name = "has_open_dependency", description = "Filter by open dependency status: true = has open dependencies, false = no open dependencies.", required = false) Boolean hasOpenDependency,
            @ToolArg(name = "search", description = "Case-insensitive text search on title and description. Surrounding whitespace is ignored. A bare integer (optionally '#' prefixed, e.g. '36' or '#36') also matches the issue with that id exactly; mixed terms like '36 fix' search text only.", required = false) String search
    ) {
        IssueQuery query = new IssueQuery();
        query.status = status;
        query.parentId = parentId;
        query.labels = labels;
        // Assignee presence, three modes: a non-empty assignee names an exact
        // match and wins over the flag (the REST rule); the flag alone filters by
        // presence; the empty string keeps meaning unassigned, the one mode
        // expressible before the flag existed.
        if (assignee != null && !assignee.isEmpty()) {
            query.assignee = assignee;
        } else if (hasAssignee != null) {
            query.hasAssignee = hasAssignee;
        } else if (assignee != null) {
            query.hasAssignee = false;
        }
        query.hasOpenDependency = hasOpenDependency;
        query.search = search;
        query.offset = 0;
        query.limit = 200;

        List<IssueService.IssueListRow> rows = issueService.listIssues(query);
        return IssueListDto.IssueListRowDto.fromRows(rows);
    }

    // ── 2. get_issue ──────────────────────────────────────────────────────

    @ToolGuardrails(input = UnknownToolArgsGuardrail.class)
    @Tool(name = "get_issue", description = "Get full details of a single issue, including its comments and its children — the children oldest-created-first (createdAt asc, id asc), uncapped." + ARG_NAMING_NOTE)
    public Map<String, Object> getIssue(
            @ToolArg(name = "id", description = "The issue ID.") Long id
    ) {
        IssueService.IssueWithChildren detail = findIssueWithChildrenOrThrow(id);
        List<CommentDto> comments = issueService.getComments(id).stream()
                .map(CommentDto::fromComment)
                .toList();
        IssueDto issueDto = IssueDto.fromIssue(detail.issue(), detail.children());
        return Map.of("issue", issueDto, "comments", comments);
    }

    // ── 3. create_issue ───────────────────────────────────────────────────

    @ToolGuardrails(input = UnknownToolArgsGuardrail.class)
    @Tool(name = "create_issue", description = "Create a new issue in the tracker." + ARG_NAMING_NOTE)
    public IssueDto createIssue(
            @ToolArg(name = "title", description = "Issue title (required).") String title,
            @ToolArg(name = "description", description = "Issue description. Defaults to empty string.", required = false) String description,
            @ToolArg(name = "parent_id", description = "Parent issue ID; the new issue becomes a child of this parent.", required = false) Long parentId,
            @ToolArg(name = "labels", description = "Initial labels to attach to the issue.", required = false) List<String> labels
    ) {
        try {
            Issue issue = issueService.createIssue(
                    title,
                    description,
                    parentId,
                    labels != null ? new java.util.HashSet<>(labels) : null
            );
            return IssueDto.fromIssue(issue);
        } catch (IllegalArgumentException e) {
            throw new McpException(e.getMessage(), JsonRpcErrorCodes.INVALID_PARAMS);
        } catch (NotFoundException e) {
            throw new McpException(e.getMessage(), JsonRpcErrorCodes.INVALID_PARAMS);
        } catch (HierarchyDepthExceededException e) {
            throw new McpException(e.getMessage(), IssueService.HIERARCHY_DEPTH_EXCEEDED_CODE);
        }
    }

    // ── 4. update_issue ──────────────────────────────────────────────────

    @ToolGuardrails(input = UnknownToolArgsGuardrail.class)
    @Tool(name = "update_issue", description = "Update an issue's mutable fields. Null/absent parameters mean 'don't change'. Labels is a full-array replacement: absent = don't change, empty array = remove all." + ARG_NAMING_NOTE)
    public IssueDto updateIssue(
            @ToolArg(name = "id", description = "The issue ID.") Long id,
            @ToolArg(name = "title", description = "New title. Omit to keep current.", required = false) String title,
            @ToolArg(name = "description", description = "New description. Omit to keep current.", required = false) String description,
            @ToolArg(name = "assignee", description = "New assignee. Omit to keep current.", required = false) String assignee,
            @ToolArg(name = "labels", description = "Full replacement labels array. Omit to keep current, empty array to remove all.", required = false) List<String> labels,
            @ToolArg(name = "parent_id", description = "New parent issue ID. Omit to keep current. To remove the parent, use unparent_issue.", required = false) Long parentId
    ) {
        try {
            Issue issue = issueService.updateIssue(
                    id, title, description, assignee,
                    labels != null ? new java.util.HashSet<>(labels) : null,
                    // MCP cannot express "explicit null": a null parent_id means "keep",
                    // so unparenting goes through unparent_issue instead.
                    parentId != null ? IssueService.ParentUpdate.set(parentId)
                            : IssueService.ParentUpdate.unchanged()
            );
            return IssueDto.fromIssue(issue);
        } catch (NotFoundException e) {
            throw new McpException(e.getMessage(), JsonRpcErrorCodes.INVALID_PARAMS);
        } catch (CycleDetectedException e) {
            throw new McpException(e.getMessage(), IssueService.CYCLE_DETECTED_CODE);
        } catch (HierarchyDepthExceededException e) {
            throw new McpException(e.getMessage(), IssueService.HIERARCHY_DEPTH_EXCEEDED_CODE);
        }
    }

    // ── 5. close_issue ───────────────────────────────────────────────────

    @ToolGuardrails(input = UnknownToolArgsGuardrail.class)
    @Tool(name = "close_issue", description = "Close an issue. A closing comment is required. Author is also required." + ARG_NAMING_NOTE)
    public IssueDto closeIssue(
            @ToolArg(name = "id", description = "The issue ID.") Long id,
            @ToolArg(name = "comment", description = "Required closing comment.") String comment,
            @ToolArg(name = "author", description = "Required author of the closing comment.") String author
    ) {
        try {
            Issue issue = issueService.closeIssue(id, comment, author);
            return IssueDto.fromIssue(issue);
        } catch (IllegalArgumentException e) {
            throw new McpException(e.getMessage(), JsonRpcErrorCodes.INVALID_PARAMS);
        } catch (NotFoundException e) {
            throw new McpException(e.getMessage(), JsonRpcErrorCodes.INVALID_PARAMS);
        } catch (InvalidStatusTransitionException e) {
            throw new McpException(e.getMessage(), JsonRpcErrorCodes.INVALID_PARAMS);
        }
    }

    // ── 6. reopen_issue ────────────────────────────────────────────────────

    @ToolGuardrails(input = UnknownToolArgsGuardrail.class)
    @Tool(name = "reopen_issue", description = "Reopen a closed issue. A reopening comment is required. Author is also required." + ARG_NAMING_NOTE)
    public IssueDto reopenIssue(
            @ToolArg(name = "id", description = "The issue ID.") Long id,
            @ToolArg(name = "comment", description = "Required reopening comment.") String comment,
            @ToolArg(name = "author", description = "Required author of the reopening comment.") String author
    ) {
        try {
            Issue issue = issueService.reopenIssue(id, comment, author);
            return IssueDto.fromIssue(issue);
        } catch (IllegalArgumentException e) {
            throw new McpException(e.getMessage(), JsonRpcErrorCodes.INVALID_PARAMS);
        } catch (NotFoundException e) {
            throw new McpException(e.getMessage(), JsonRpcErrorCodes.INVALID_PARAMS);
        } catch (InvalidStatusTransitionException e) {
            throw new McpException(e.getMessage(), JsonRpcErrorCodes.INVALID_PARAMS);
        }
    }

    // ── 7. unassign_issue ─────────────────────────────────────────────────

    @ToolGuardrails(input = UnknownToolArgsGuardrail.class)
    @Tool(name = "unassign_issue", description = "Remove the assignee from an issue (set assignee to null)." + ARG_NAMING_NOTE)
    public IssueDto unassignIssue(
            @ToolArg(name = "id", description = "The issue ID.") Long id
    ) {
        Issue issue = findIssueOrThrow(id);
        issue = issueService.unassignIssue(id);
        return IssueDto.fromIssue(issue);
    }

    // ── 7b. unparent_issue ────────────────────────────────────────────────

    @ToolGuardrails(input = UnknownToolArgsGuardrail.class)
    @Tool(name = "unparent_issue", description = "Remove the parent from an issue (set parent to null). Separate from update_issue because MCP arguments cannot express 'explicit null'." + ARG_NAMING_NOTE)
    public IssueDto unparentIssue(
            @ToolArg(name = "id", description = "The issue ID.") Long id
    ) {
        findIssueOrThrow(id);
        Issue issue = issueService.reparent(id, null);
        return IssueDto.fromIssue(issue);
    }

    // ── 8. add_dependency ──────────────────────────────────────────────────

    @ToolGuardrails(input = UnknownToolArgsGuardrail.class)
    @Tool(name = "add_dependency", description = "Add a dependency relationship: the dependent issue depends on the dependency issue. Rejects cycles." + ARG_NAMING_NOTE)
    public String addDependency(
            @ToolArg(name = "dependent_id", description = "The issue that depends (the waiting issue).", required = true) Long dependentId,
            @ToolArg(name = "dependency_id", description = "The issue being depended on (the prerequisite).", required = true) Long dependencyId
    ) {
        try {
            issueService.addDependency(dependentId, dependencyId);
            return "Dependency added: issue " + dependentId + " now depends on issue " + dependencyId;
        } catch (NotFoundException e) {
            throw new McpException(e.getMessage(), JsonRpcErrorCodes.INVALID_PARAMS);
        } catch (CycleDetectedException e) {
            throw new McpException(e.getMessage(), IssueService.CYCLE_DETECTED_CODE);
        }
    }

    // ── 9. remove_dependency ───────────────────────────────────────────────

    @ToolGuardrails(input = UnknownToolArgsGuardrail.class)
    @Tool(name = "remove_dependency", description = "Remove a dependency relationship." + ARG_NAMING_NOTE)
    public String removeDependency(
            @ToolArg(name = "dependent_id", description = "The issue that no longer depends.") Long dependentId,
            @ToolArg(name = "dependency_id", description = "The issue that is no longer a dependency.") Long dependencyId
    ) {
        try {
            issueService.removeDependency(dependentId, dependencyId);
            return "Dependency removed: issue " + dependentId + " no longer depends on issue " + dependencyId;
        } catch (NotFoundException e) {
            throw new McpException(e.getMessage(), JsonRpcErrorCodes.INVALID_PARAMS);
        }
    }

    // ── 10. list_dependencies ───────────────────────────────────────────────

    @ToolGuardrails(input = UnknownToolArgsGuardrail.class)
    @Tool(name = "list_dependencies", description = "List issues that this issue depends on (prerequisites)." + ARG_NAMING_NOTE)
    public List<IssueListDto.IssueSummaryDto> listDependencies(
            @ToolArg(name = "id", description = "The issue ID.") Long id
    ) {
        findIssueOrThrow(id);
        return issueService.listDependencies(id).stream()
                .map(IssueListDto.IssueSummaryDto::fromIssue)
                .toList();
    }

    // ── 11. list_dependents ────────────────────────────────────────────────

    @ToolGuardrails(input = UnknownToolArgsGuardrail.class)
    @Tool(name = "list_dependents", description = "List issues that depend on this issue (waiters)." + ARG_NAMING_NOTE)
    public List<IssueListDto.IssueSummaryDto> listDependents(
            @ToolArg(name = "id", description = "The issue ID.") Long id
    ) {
        findIssueOrThrow(id);
        return issueService.listDependents(id).stream()
                .map(IssueListDto.IssueSummaryDto::fromIssue)
                .toList();
    }

    // ── 12. list_children ─────────────────────────────────────────────────

    @ToolGuardrails(input = UnknownToolArgsGuardrail.class)
    @Tool(name = "list_children", description = "List child issues of a parent issue, oldest-created-first (createdAt asc, id asc) — the same order get_issue, the issue:///{id} resource, and the children a list_issues row embeds serve." + ARG_NAMING_NOTE)
    public List<IssueListDto.IssueSummaryDto> listChildren(
            @ToolArg(name = "parent_id", description = "The parent issue ID.") Long parentId
    ) {
        return issueService.listChildren(parentId).stream()
                .map(IssueListDto.IssueSummaryDto::fromIssue)
                .toList();
    }

    // ── 13. list_frontier ─────────────────────────────────────────────────

    @ToolGuardrails(input = UnknownToolArgsGuardrail.class)
    @Tool(name = "list_frontier", description = "List frontier issues: open, no open dependencies, and unclaimed. Optionally filtered by parent issue ID. Every frontier issue is its own row (flat, not grouped — the frontier is the actionable set); each row embeds childCounts, the open/closed/total counts of the issue's first-level children. Returned oldest-created-first (createdAt asc, id asc), fixed — for a tracker that appends, the picking convention (take the first row, lowest number wins) falls out of the list order directly." + ARG_NAMING_NOTE)
    public List<IssueListDto.IssueListRowDto> listFrontier(
            @ToolArg(name = "parent_id", description = "Optional parent issue ID to filter by.", required = false) Long parentId
    ) {
        return IssueListDto.IssueListRowDto.fromRows(issueService.listFrontier(parentId));
    }

    // ── 14. claim_issue ───────────────────────────────────────────────────

    @ToolGuardrails(input = UnknownToolArgsGuardrail.class)
    @Tool(name = "claim_issue", description = "Claim an issue for an agent. Sets assignee to the agent name. Fails if already claimed by a different agent." + ARG_NAMING_NOTE)
    public IssueDto claimIssue(
            @ToolArg(name = "id", description = "The issue ID.") Long id,
            @ToolArg(name = "agent_name", description = "The claiming agent's name.") String agentName
    ) {
        try {
            Issue issue = issueService.claimIssue(id, agentName);
            return IssueDto.fromIssue(issue);
        } catch (IllegalArgumentException e) {
            throw new McpException(e.getMessage(), JsonRpcErrorCodes.INVALID_PARAMS);
        } catch (NotFoundException e) {
            throw new McpException(e.getMessage(), JsonRpcErrorCodes.INVALID_PARAMS);
        } catch (AlreadyClaimedException e) {
            throw new McpException(e.getMessage(), JsonRpcErrorCodes.INVALID_PARAMS);
        }
    }

    // ── 15. comment ────────────────────────────────────────────────────────

    @ToolGuardrails(input = UnknownToolArgsGuardrail.class)
    @Tool(name = "comment", description = "Add a comment to an issue. Body is required." + ARG_NAMING_NOTE)
    public CommentDto comment(
            @ToolArg(name = "id", description = "The issue ID.") Long id,
            @ToolArg(name = "author", description = "The comment author's name.") String author,
            @ToolArg(name = "body", description = "The comment body in markdown (required).", required = true) String body
    ) {
        try {
            Comment comment = issueService.addComment(id, author, body);
            return CommentDto.fromComment(comment);
        } catch (IllegalArgumentException e) {
            throw new McpException(e.getMessage(), JsonRpcErrorCodes.INVALID_PARAMS);
        } catch (NotFoundException e) {
            throw new McpException(e.getMessage(), JsonRpcErrorCodes.INVALID_PARAMS);
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private Issue findIssueOrThrow(Long id) {
        try {
            return issueService.getIssue(id);
        } catch (NotFoundException e) {
            throw new McpException(e.getMessage(), JsonRpcErrorCodes.INVALID_PARAMS);
        }
    }

    /** The issue with the children its detail payload embeds, or a MCP not-found error. */
    private IssueService.IssueWithChildren findIssueWithChildrenOrThrow(Long id) {
        try {
            return issueService.getIssueWithChildren(id);
        } catch (NotFoundException e) {
            throw new McpException(e.getMessage(), JsonRpcErrorCodes.INVALID_PARAMS);
        }
    }
}
