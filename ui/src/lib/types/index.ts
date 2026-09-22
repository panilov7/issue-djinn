// Hand-written TypeScript types mirroring the issue-djinn REST API.
// Keep these in sync with the Java DTOs under
// `src/main/java/com/example/issuedjinn/...` (issue-djinn backend).

export type IssueStatus = 'open' | 'closed';

/**
 * The status filter's value, as a status-filtering surface holds it.
 * `'all'` means "no status restriction" — the deselect fallback, and the value
 * the API client serializes as param omission (the API has no `all` status).
 */
export type IssueStatusFilter = 'all' | IssueStatus;

/**
 * The work-state filter's value — which of the two work-state segments is
 * pressed, if either: `'frontier'` (open, unclaimed, with no open
 * dependencies — the frontier) or `'inProgress'` (open, claimed, with no
 * open dependencies — the work happening right now). Absent means the
 * control is deselected: no work-state restriction, status per its own
 * control. The presets are UI-side compositions over the REST filter params
 * — no server-side work-state exists (CONTEXT.md → Work-state filter).
 */
export type WorkFilter = 'frontier' | 'inProgress';

/**
 * The field parent-level rows are ordered by — the whitelisted `sort` values of
 * the REST listing contract (a sort-field value matches the JSON camelCase
 * spelling). Children are ordered by their own `createdAt` and take
 * {@link SortDirection} instead.
 */
export type IssueSortField = 'createdAt' | 'updatedAt' | 'title' | 'id';

/** The direction a listing runs in — the `asc|desc` values of `direction` and `child_direction`. */
export type SortDirection = 'asc' | 'desc';

/** The light row shape an issue carries wherever it appears as a row: the issues list and a detail payload's children. */
export interface IssueSummary {
	id: number;
	title: string;
	status: IssueStatus;
	assignee: string | null;
	labels: string[];
	parentId: number | null;
	updatedAt: string;
}

/**
 * The per-root child counts a list row embeds (`childCounts { open, closed, total }`).
 * Taken over all of the root's first-level children, whatever the active filters are.
 */
export interface ChildCounts {
	open: number;
	closed: number;
	total: number;
}

/**
 * One row in the light list shape of `GET /api/issues`.
 *
 * A row is a root issue; its filter-matching first-level children ride along
 * under it (see `children`), which is why pagination counts roots and a child
 * never takes a row of its own.
 */
export interface IssueListItem extends IssueSummary {
	/** First-level children of this row's root, counted regardless of the filters. */
	childCounts: ChildCounts;
	/**
	 * Whether this root itself matched the active filters. `false` marks a
	 * structural container: it is listed only because a child matched, so the
	 * UI dims it.
	 */
	matchesFilter: boolean;
	/**
	 * How many of this root's first-level children matched the filters in
	 * total — the `children` it embeds are capped, and this count is what the
	 * overflow row turns into "…and N more".
	 */
	matchingChildCount: number;
	/**
	 * The embedded children, capped at the first ten of the child order —
	 * oldest-created-first (`createdAt asc, id asc`) unless the request's
	 * `child_direction` flips them (CONTEXT.md → Listing order).
	 */
	children: IssueSummary[];
}

/**
 * A child issue embedded in the detail shape — the summary row without counts,
 * as `GET /api/issues/{id}` hands them over.
 */
export type IssueChild = IssueSummary;

/** A dependency/dependent summary embedded in the detail shape. */
export interface IssueDependency {
	id: number;
	title: string;
	status: IssueStatus;
}

/** The inline detail shape of `GET /api/issues/{id}`. */
export interface IssueDetail {
	id: number;
	title: string;
	description: string;
	status: IssueStatus;
	assignee: string | null;
	createdAt: string;
	updatedAt: string;
	parentId: number | null;
	labels: string[];
	commentCount: number;
	dependencies: IssueDependency[];
	dependents: IssueDependency[];
	/** Uncapped; oldest-created-first unless the request's `child_direction` flips them. */
	children: IssueChild[];
}

/** A comment on an issue (`GET /api/issues/{id}/comments`). */
export interface Comment {
	id: number;
	author: string;
	body: string;
	createdAt: string;
}

export interface IssueListResponse {
	issues: IssueListItem[];
}

export interface CommentsListResponse {
	comments: Comment[];
}

export interface LabelsResponse {
	labels: string[];
}

/** What a change event says happened — also the SSE event name on the wire. */
export type IssueChangeType = 'issue_created' | 'issue_updated' | 'issue_deleted';

/**
 * The JSON data payload of one SSE frame from `GET /api/events`, mirroring the
 * backend's `IssueEventMessage`. Events carry no entity bodies — the UI's
 * reaction is always a refetch of what the event names.
 */
export interface IssueChangeEvent {
	type: IssueChangeType;
	/** Per-process monotonically increasing sequence number (repeated in the SSE `id:` field). */
	seq: number;
	/** The mutated issue first, then its direct counterparts. */
	issueIds: number[];
	/** When the change happened (ISO timestamp, UTC). */
	at: string;
}

/** Query-string parameters accepted by `GET /api/issues`. */
export interface IssueListParams {
	/** The status filter's value; `'all'` is serialized as omission. */
	status?: IssueStatusFilter;
	parent?: number;
	/**
	 * True lists every matching issue as its own row, with nothing grouped under
	 * it — what a picker needs, since a cap on embedded children would silently
	 * hide pickable issues.
	 */
	flat?: boolean;
	labels?: string[];
	assignee?: string;
	hasAssignee?: boolean;
	hasOpenDependency?: boolean;
	/** The field parent-level rows run in — children take `childDirection` instead. */
	sort?: IssueSortField;
	/** The direction parent-level rows run in; the id tie-breaker flips with it. */
	direction?: SortDirection;
	/** The direction children are served in, wherever children appear. */
	childDirection?: SortDirection;
	search?: string;
	offset?: number;
	limit?: number;
}
