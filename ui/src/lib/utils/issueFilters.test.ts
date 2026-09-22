import { describe, expect, it } from 'vitest';
import {
	buildIssueParams,
	defaultFilterSnapshot,
	filterSnapshot,
	initialFilters,
	isDefaultFilterSnapshot,
	parseFilterSnapshot
} from './issueFilters';

describe('buildIssueParams', () => {
	it('defaults to the frontier query with the default sort', () => {
		const params = buildIssueParams(initialFilters());
		expect(params).toEqual({
			status: 'open',
			hasOpenDependency: false,
			hasAssignee: false,
			labels: [],
			// Parents run newest-created-first, children oldest-created-first —
			// the defaults CONTEXT.md's listing-order rule pins.
			sort: 'createdAt',
			direction: 'desc',
			childDirection: 'asc',
			offset: 0,
			limit: 50
		});
	});

	it('carries a chosen parent sort and child direction through to the params', () => {
		const params = buildIssueParams({
			...initialFilters(),
			sortField: 'title',
			direction: 'asc',
			childDirection: 'desc'
		});
		expect(params.sort).toBe('title');
		expect(params.direction).toBe('asc');
		expect(params.childDirection).toBe('desc');
	});

	it('sends the status selection and no work-state params when the work-state control is deselected', () => {
		const filters = { ...initialFilters(), workFilter: undefined };
		const params = buildIssueParams(filters);
		expect(params).toEqual({
			status: 'open',
			labels: [],
			sort: 'createdAt',
			direction: 'desc',
			childDirection: 'asc',
			offset: 0,
			limit: 50
		});
		expect(params.hasOpenDependency).toBeUndefined();
		expect(params.hasAssignee).toBeUndefined();
	});

	it('sends the In progress preset — open, claimed, unblocked', () => {
		const params = buildIssueParams({ ...initialFilters(), workFilter: 'inProgress' });
		expect(params.status).toBe('open');
		expect(params.hasAssignee).toBe(true);
		expect(params.hasOpenDependency).toBe(false);
	});

	it('reflects a closed status selection when the work-state control is deselected', () => {
		const params = buildIssueParams({
			...initialFilters(),
			workFilter: undefined,
			status: 'closed'
		});
		expect(params.status).toBe('closed');
	});

	it('keeps the "all" status filter when deselected (the client omits the param)', () => {
		const params = buildIssueParams({ ...initialFilters(), workFilter: undefined, status: 'all' });
		expect(params.status).toBe('all');
	});

	it('passes selected labels through (AND semantics are server-side)', () => {
		const params = buildIssueParams({ ...initialFilters(), labels: ['needs-triage', 'task'] });
		expect(params.labels).toEqual(['needs-triage', 'task']);
	});

	it('includes search text and pagination', () => {
		const params = buildIssueParams({
			...initialFilters(),
			search: 'quarkus',
			page: 3,
			pageSize: 25
		});
		expect(params.search).toBe('quarkus');
		expect(params.offset).toBe(50);
		expect(params.limit).toBe(25);
	});

	it('omits the search param when search is empty', () => {
		const params = buildIssueParams({ ...initialFilters(), search: '' });
		expect(params.search).toBeUndefined();
	});
});

describe('isDefaultFilterSnapshot', () => {
	it('matches the default frontier view', () => {
		expect(isDefaultFilterSnapshot(defaultFilterSnapshot())).toBe(true);
	});

	it('is false when the work-state control is deselected', () => {
		expect(
			isDefaultFilterSnapshot({ ...defaultFilterSnapshot(), workFilter: undefined })
		).toBe(false);
	});

	it('is false when the In progress segment is selected', () => {
		expect(
			isDefaultFilterSnapshot({ ...defaultFilterSnapshot(), workFilter: 'inProgress' })
		).toBe(false);
	});

	it('is false when a status other than open is selected', () => {
		expect(isDefaultFilterSnapshot({ ...defaultFilterSnapshot(), status: 'closed' })).toBe(false);
	});

	it('is false when any label is selected', () => {
		expect(isDefaultFilterSnapshot({ ...defaultFilterSnapshot(), labels: ['needs-triage'] })).toBe(
			false
		);
	});

	it('is false when search text is applied', () => {
		expect(isDefaultFilterSnapshot({ ...defaultFilterSnapshot(), search: 'quarkus' })).toBe(false);
	});

	it('is false when the parent sort diverges from the newest-created-first default', () => {
		expect(
			isDefaultFilterSnapshot({ ...defaultFilterSnapshot(), sortField: 'title', direction: 'asc' })
		).toBe(false);
	});

	it('is false when the child direction diverges from the oldest-first default', () => {
		expect(isDefaultFilterSnapshot({ ...defaultFilterSnapshot(), childDirection: 'desc' })).toBe(
			false
		);
	});

	it('is true when the sort fields equal the defaults', () => {
		expect(isDefaultFilterSnapshot({ ...defaultFilterSnapshot(), sortField: 'createdAt' })).toBe(
			true
		);
	});
});

describe('filterSnapshot / parseFilterSnapshot', () => {
	it('round-trips the sort fields and the work state with the filter fields', () => {
		const filters = {
			...initialFilters(),
			sortField: 'updatedAt' as const,
			direction: 'asc' as const,
			childDirection: 'desc' as const
		};
		const stored = filterSnapshot(filters);
		expect(stored).toEqual({
			status: 'open',
			labels: [],
			workFilter: 'frontier',
			search: '',
			sortField: 'updatedAt',
			direction: 'asc',
			childDirection: 'desc'
		});
		expect(parseFilterSnapshot(JSON.parse(JSON.stringify(stored)))).toEqual(stored);
	});

	it('round-trips a deselected work state as an absent workFilter key', () => {
		const filters = { ...initialFilters(), workFilter: undefined };
		// JSON.stringify drops the undefined key: the snapshot stores `workFilter`
		// as `frontier` / `inProgress` / absent, so a deselect survives a reload
		// without a third value on the wire.
		const blob = JSON.parse(JSON.stringify(filterSnapshot(filters)));
		expect('workFilter' in blob).toBe(false);
		expect(parseFilterSnapshot(blob)).toEqual({
			status: 'open',
			labels: [],
			search: '',
			sortField: 'createdAt',
			direction: 'desc',
			childDirection: 'asc'
		});
	});

	it('rejects a chip-era snapshot carrying the legacy frontier boolean (the one-time reset)', () => {
		// A pre-work-state blob is not migrated: it fails validation and the
		// default frontier view takes over — the one-time silent reset the
		// design accepts.
		expect(
			parseFilterSnapshot({
				status: 'open',
				labels: [],
				frontier: true,
				search: '',
				sortField: 'createdAt',
				direction: 'desc',
				childDirection: 'asc'
			})
		).toBeUndefined();
	});

	it('rejects a pre-sort snapshot missing the sort fields (the one-time reset)', () => {
		expect(
			parseFilterSnapshot({ status: 'closed', labels: [], search: 'quarkus' })
		).toBeUndefined();
	});

	it.each([
		['workFilter is off the whitelist', { workFilter: 'claimed' }],
		['workFilter is a boolean', { workFilter: true }],
		['sortField is off the whitelist', { sortField: 'assignee' }],
		['direction is off the whitelist', { direction: 'newest' }],
		['childDirection is off the whitelist', { childDirection: 'oldest' }],
		['sortField is missing', { sortField: undefined }],
		['childDirection is missing', { childDirection: undefined }]
	])('rejects a snapshot when %s', (_, overrides) => {
		expect(parseFilterSnapshot({ ...defaultFilterSnapshot(), ...overrides })).toBeUndefined();
	});
});
