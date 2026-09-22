// Pure mapping from the UI's filter state to the `GET /api/issues` query
// string parameters (see `src/lib/api/issues.ts`). Kept side-effect-free so it
// can be unit-tested and shared by the `+page.ts` load (initial frontier view)
// and the `IssueList` component (re-fetch on every filter change).

import type {
	IssueListParams,
	IssueSortField,
	IssueStatusFilter,
	SortDirection,
	WorkFilter
} from '$lib/types';

export interface IssueFilters {
	/** Selected status while the work-state control is deselected; `'all'` lists both statuses. */
	status: IssueStatusFilter;
	/** Selected labels, AND'd together by the API. */
	labels: string[];
	/** The pressed work-state segment, if either; absent when the control is deselected. */
	workFilter?: WorkFilter;
	/** Debounced search text. */
	search: string;
	/** The field parent rows are ordered by — children always run by their own `createdAt`. */
	sortField: IssueSortField;
	/** The direction parent rows run in; children take `childDirection`. */
	direction: SortDirection;
	/** The direction every embedded child row on the page runs in. */
	childDirection: SortDirection;
	/** 1-based page number. */
	page: number;
	pageSize: number;
}

export const DEFAULT_PAGE_SIZE = 50;

/** The user-facing filter and sort fields that persist across reloads; pagination is not persisted. */
export type FilterSnapshot = Pick<
	IssueFilters,
	'status' | 'labels' | 'workFilter' | 'search' | 'sortField' | 'direction' | 'childDirection'
>;

/** The filters a fresh visit starts on — the default frontier view, parents newest-first, children oldest-first. */
export function defaultFilterSnapshot(): FilterSnapshot {
	return {
		status: 'open',
		labels: [],
		workFilter: 'frontier',
		search: '',
		sortField: 'createdAt',
		direction: 'desc',
		childDirection: 'asc'
	};
}

/**
 * True when the persisted fields deep-equal the default frontier view's —
 * the reset button's disabled state. Pagination is not part of the comparison.
 */
export function isDefaultFilterSnapshot(snapshot: FilterSnapshot): boolean {
	const defaults = defaultFilterSnapshot();
	return (
		snapshot.status === defaults.status &&
		snapshot.workFilter === defaults.workFilter &&
		snapshot.search === defaults.search &&
		snapshot.sortField === defaults.sortField &&
		snapshot.direction === defaults.direction &&
		snapshot.childDirection === defaults.childDirection &&
		snapshot.labels.length === defaults.labels.length &&
		snapshot.labels.every((label) => defaults.labels.includes(label))
	);
}

export function initialFilters(): IssueFilters {
	return { ...defaultFilterSnapshot(), page: 1, pageSize: DEFAULT_PAGE_SIZE };
}

/** The persisted view of the filters: the user-facing filter and sort fields, nothing else. */
export function filterSnapshot(filters: IssueFilters): FilterSnapshot {
	return {
		status: filters.status,
		labels: filters.labels,
		workFilter: filters.workFilter,
		search: filters.search,
		sortField: filters.sortField,
		direction: filters.direction,
		childDirection: filters.childDirection
	};
}

const STATUS_FILTERS: readonly string[] = ['all', 'open', 'closed'];

function isStatusFilter(value: unknown): value is IssueStatusFilter {
	return typeof value === 'string' && STATUS_FILTERS.includes(value);
}

function isStringArray(value: unknown): value is string[] {
	return Array.isArray(value) && value.every((item) => typeof item === 'string');
}

const SORT_FIELDS: readonly string[] = ['createdAt', 'updatedAt', 'title', 'id'];

/** The REST `sort` whitelist, as a narrow for untyped strings riding out of primitives. */
export function isSortField(value: unknown): value is IssueSortField {
	return typeof value === 'string' && SORT_FIELDS.includes(value);
}

const SORT_DIRECTIONS: readonly string[] = ['asc', 'desc'];

/** The `asc|desc` whitelist shared by `direction` and `child_direction`, as a narrow. */
export function isSortDirection(value: unknown): value is SortDirection {
	return typeof value === 'string' && SORT_DIRECTIONS.includes(value);
}

const WORK_FILTERS: readonly string[] = ['frontier', 'inProgress'];

/** The work-state segment whitelist, as a narrow for untyped strings riding out of primitives. */
export function isWorkFilter(value: unknown): value is WorkFilter {
	return typeof value === 'string' && WORK_FILTERS.includes(value);
}

/**
 * Validates a stored snapshot blob, returning the persisted filter and sort
 * fields — or `undefined` when the blob is corrupt or wrong-shaped, so the
 * caller can fall back to the defaults. Extra keys (e.g. a no-longer-persisted
 * `page`) ride along unvalidated; a blob carrying the chip-era `frontier`
 * boolean is rejected wholesale — old snapshots are not migrated, they reset
 * to the defaults once. `workFilter` is optional: a deselected work state
 * stores no key at all.
 */
export function parseFilterSnapshot(raw: unknown): FilterSnapshot | undefined {
	if (typeof raw !== 'object' || raw === null) return undefined;
	// The per-key checks below do the real validation; this only exposes the
	// keys for them to read.
	const blob = raw as Record<string, unknown>;
	if ('frontier' in blob) return undefined;
	if (!isStatusFilter(blob.status)) return undefined;
	if (!isStringArray(blob.labels)) return undefined;
	if (blob.workFilter !== undefined && !isWorkFilter(blob.workFilter)) return undefined;
	if (typeof blob.search !== 'string') return undefined;
	if (!isSortField(blob.sortField)) return undefined;
	if (!isSortDirection(blob.direction)) return undefined;
	if (!isSortDirection(blob.childDirection)) return undefined;
	return {
		status: blob.status,
		labels: blob.labels,
		workFilter: blob.workFilter,
		search: blob.search,
		sortField: blob.sortField,
		direction: blob.direction,
		childDirection: blob.childDirection
	};
}

export function buildIssueParams(filters: IssueFilters): IssueListParams {
	const params: IssueListParams = {
		labels: filters.labels,
		// The sort is always sent: the view always has one, and the request then
		// spells out the whole arrangement instead of leaning on API defaults.
		sort: filters.sortField,
		direction: filters.direction,
		childDirection: filters.childDirection,
		offset: (filters.page - 1) * filters.pageSize,
		limit: filters.pageSize
	};
	if (filters.search) params.search = filters.search;

	if (filters.workFilter !== undefined) {
		// A work-state preset is always open and excludes issues with open
		// dependencies; the segments differ on the assignee alone.
		params.status = 'open';
		params.hasOpenDependency = false;
		params.hasAssignee = filters.workFilter === 'inProgress';
	} else {
		params.status = filters.status;
	}

	return params;
}
