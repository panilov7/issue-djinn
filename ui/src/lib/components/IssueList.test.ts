import { fireEvent, render, screen, waitFor } from '@testing-library/svelte';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import IssueList from './IssueList.svelte';
import { issueEvents } from '$lib/stores/issueEvents.svelte';
import type { IssueChangeEvent, IssueChangeType, IssueListItem } from '$lib/types';

function jsonResponse(body: unknown, status = 200): Response {
	return {
		ok: status >= 200 && status < 300,
		status,
		json: () => Promise.resolve(body)
	} as Response;
}

const labels = ['needs-triage', 'task'];

const openIssue: IssueListItem = {
	id: 3,
	title: 'Quarkus MCP research',
	status: 'open',
	assignee: null,
	labels: ['needs-triage'],
	parentId: null,
	updatedAt: new Date().toISOString(),
	childCounts: { open: 0, closed: 0, total: 0 },
	matchesFilter: true,
	matchingChildCount: 0,
	children: []
};

const closedIssue: IssueListItem = {
	id: 1,
	title: 'Bootstrap the tracker',
	status: 'closed',
	assignee: 'bob',
	labels: ['task'],
	parentId: null,
	updatedAt: new Date().toISOString(),
	childCounts: { open: 0, closed: 0, total: 0 },
	matchesFilter: true,
	matchingChildCount: 0,
	children: []
};

function lastFetchUrl(fetchMock: ReturnType<typeof vi.fn>): string {
	const calls = fetchMock.mock.calls;
	return calls[calls.length - 1][0] as string;
}

const FILTERS_KEY = 'issue-djinn.filters';

// What the persisted-state utility last wrote, or `null` when nothing was.
function storedSnapshot(): unknown {
	const raw = localStorage.getItem(FILTERS_KEY);
	return raw === null ? null : JSON.parse(raw);
}

describe('IssueList', () => {
	beforeEach(() => {
		vi.useRealTimers();
		localStorage.clear();
	});
	afterEach(() => vi.unstubAllGlobals());

	it('renders the Frontier segment pressed by default and loads the frontier query', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: [openIssue] }));
		vi.stubGlobal('fetch', fetchMock);

		render(IssueList, { props: { labels } });

		expect(await screen.findByText('Quarkus MCP research')).toBeInTheDocument();
		expect(screen.getByRole('radio', { name: 'Frontier' })).toBeChecked();

		const url = lastFetchUrl(fetchMock);
		expect(url).toContain('status=open');
		expect(url).toContain('has_assignee=false');
		expect(url).toContain('has_open_dependency=false');
	});

	it('re-fetches without work-state constraints when the Frontier segment is deselected', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: [openIssue, closedIssue] }));
		vi.stubGlobal('fetch', fetchMock);

		render(IssueList, { props: { labels } });
		await screen.findByText('Quarkus MCP research');

		await fireEvent.click(screen.getByRole('radio', { name: 'Frontier' }));

		await waitFor(() => {
			const url = lastFetchUrl(fetchMock);
			expect(url).toContain('status=open');
			expect(url).not.toContain('has_assignee');
			expect(url).not.toContain('has_open_dependency');
		});
	});

	it('re-fetches with the In progress preset when that segment is selected', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: [openIssue] }));
		vi.stubGlobal('fetch', fetchMock);

		render(IssueList, { props: { labels } });
		await screen.findByText('Quarkus MCP research');

		// Frontier is on by default; selecting the other segment flips the
		// work-state restriction to claimed work.
		await fireEvent.click(screen.getByRole('radio', { name: 'In progress' }));

		await waitFor(() => {
			const url = lastFetchUrl(fetchMock);
			expect(url).toContain('status=open');
			expect(url).toContain('has_assignee=true');
			expect(url).toContain('has_open_dependency=false');
		});
		expect(screen.getByRole('radio', { name: 'In progress' })).toBeChecked();
		expect(screen.getByRole('radio', { name: 'Frontier' })).not.toBeChecked();
	});

	it('pins the status control to open and disables it while a work state is selected', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: [openIssue] }));
		vi.stubGlobal('fetch', fetchMock);

		render(IssueList, { props: { labels } });
		await screen.findByText('Quarkus MCP research');

		expect(screen.getByRole('radio', { name: 'Open' })).toBeChecked();
		expect(screen.getByRole('radio', { name: 'All' })).toBeDisabled();
		expect(screen.getByRole('radio', { name: 'Closed' })).toBeDisabled();
	});

	it('pins a restored snapshot carrying a work state to the open status', async () => {
		// A hand-written or stale blob could carry a work state and a
		// contradictory closed status — storage from outside the app's own
		// writes cannot undo the pin: the restored view is open, and the
		// disabled status control shows it.
		localStorage.setItem(
			FILTERS_KEY,
			JSON.stringify({
				status: 'closed',
				labels: [],
				workFilter: 'inProgress',
				search: '',
				sortField: 'createdAt',
				direction: 'desc',
				childDirection: 'asc'
			})
		);
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: [openIssue] }));
		vi.stubGlobal('fetch', fetchMock);

		render(IssueList, { props: { labels } });

		expect(await screen.findByText('Quarkus MCP research')).toBeInTheDocument();
		expect(screen.getByRole('radio', { name: 'Open' })).toBeChecked();
		expect(screen.getByRole('radio', { name: 'Open' })).toBeDisabled();
		expect(lastFetchUrl(fetchMock)).toContain('status=open');
		expect(lastFetchUrl(fetchMock)).toContain('has_assignee=true');
	});

	it('re-enables the status control at its current value when the work state is deselected', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: [openIssue, closedIssue] }));
		vi.stubGlobal('fetch', fetchMock);

		render(IssueList, { props: { labels } });
		await screen.findByText('Quarkus MCP research');

		await fireEvent.click(screen.getByRole('radio', { name: 'Frontier' }));

		// The deselect leaves status where it was pinned (open) but editable
		// again — closed becomes reachable without touching the work state.
		expect(screen.getByRole('radio', { name: 'All' })).toBeEnabled();
		expect(screen.getByRole('radio', { name: 'Open' })).toBeEnabled();
		expect(screen.getByRole('radio', { name: 'Closed' })).toBeEnabled();
		expect(screen.getByRole('radio', { name: 'Open' })).toBeChecked();
	});

	it('shows closed issues when the closed status segment is selected', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: [closedIssue] }));
		vi.stubGlobal('fetch', fetchMock);

		render(IssueList, { props: { labels } });
		await screen.findByText('Bootstrap the tracker');

		// Frontier is on by default, which pins the status control. Deselect it
		// first, then select Closed.
		await fireEvent.click(screen.getByRole('radio', { name: 'Frontier' }));
		await fireEvent.click(screen.getByRole('radio', { name: /closed/i }));

		await waitFor(() => {
			expect(lastFetchUrl(fetchMock)).toContain('status=closed');
		});
		expect(screen.getByText('Bootstrap the tracker')).toBeInTheDocument();
	});

	it('lists open and closed issues together when All is selected, without a status param', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: [openIssue, closedIssue] }));
		vi.stubGlobal('fetch', fetchMock);

		render(IssueList, { props: { labels } });
		await screen.findByText('Quarkus MCP research');

		// Frontier is on by default, which pins the status control. Deselect it
		// first, then select All.
		await fireEvent.click(screen.getByRole('radio', { name: 'Frontier' }));
		await fireEvent.click(screen.getByRole('radio', { name: /all/i }));

		await waitFor(() => {
			const url = lastFetchUrl(fetchMock);
			expect(url).not.toContain('status=');
		});
		expect(screen.getByText('Quarkus MCP research')).toBeInTheDocument();
		expect(screen.getByText('Bootstrap the tracker')).toBeInTheDocument();
	});

	it('returns to All when the pressed status is deselected', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: [openIssue] }));
		vi.stubGlobal('fetch', fetchMock);

		render(IssueList, { props: { labels } });
		await screen.findByText('Quarkus MCP research');

		await fireEvent.click(screen.getByRole('radio', { name: 'Frontier' }));
		await fireEvent.click(screen.getByRole('radio', { name: /closed/i }));
		await waitFor(() => {
			expect(lastFetchUrl(fetchMock)).toContain('status=closed');
		});

		await fireEvent.click(screen.getByRole('radio', { name: /closed/i }));
		await waitFor(() => {
			expect(lastFetchUrl(fetchMock)).not.toContain('status=');
		});
		expect(screen.getByRole('radio', { name: /all/i })).toBeChecked();
	});

	it('sends selected labels as repeated label params (AND semantics)', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: [openIssue] }));
		vi.stubGlobal('fetch', fetchMock);

		render(IssueList, { props: { labels } });
		await screen.findByText('Quarkus MCP research');

		await fireEvent.click(screen.getByRole('button', { name: /labels/i }));
		await fireEvent.click(await screen.findByRole('checkbox', { name: /needs-triage/i }));

		await waitFor(() => {
			expect(lastFetchUrl(fetchMock)).toContain('label=needs-triage');
		});
	});

	it('sends the default sort — parents newest-first, children oldest-first', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: [openIssue] }));
		vi.stubGlobal('fetch', fetchMock);

		render(IssueList, { props: { labels } });

		await screen.findByText('Quarkus MCP research');
		const url = lastFetchUrl(fetchMock);
		expect(url).toContain('sort=createdAt');
		expect(url).toContain('direction=desc');
		expect(url).toContain('child_direction=asc');
	});

	it('re-fetches with the picked parent sort field', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: [openIssue] }));
		vi.stubGlobal('fetch', fetchMock);

		render(IssueList, { props: { labels } });
		await screen.findByText('Quarkus MCP research');

		await fireEvent.click(screen.getByRole('button', { name: /sort/i }));
		await fireEvent.click(await screen.findByRole('menuitemradio', { name: 'Title' }));

		await waitFor(() => {
			expect(lastFetchUrl(fetchMock)).toContain('sort=title');
		});
		expect(lastFetchUrl(fetchMock)).toContain('direction=desc');
	});

	it('re-fetches with a flipped parent sort direction', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: [openIssue] }));
		vi.stubGlobal('fetch', fetchMock);

		render(IssueList, { props: { labels } });
		await screen.findByText('Quarkus MCP research');

		// The direction flip button sits beside the Sort menu on the sorting
		// row; every click reverses the order.
		await fireEvent.click(screen.getByRole('button', { name: /parent issue order/i }));

		await waitFor(() => {
			expect(lastFetchUrl(fetchMock)).toContain('direction=asc');
		});
	});

	it('re-fetches with child_direction=desc when the child direction flips', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: [openIssue] }));
		vi.stubGlobal('fetch', fetchMock);

		render(IssueList, { props: { labels } });
		await screen.findByText('Quarkus MCP research');

		await fireEvent.click(screen.getByRole('radio', { name: 'Newest first' }));

		await waitFor(() => {
			expect(lastFetchUrl(fetchMock)).toContain('child_direction=desc');
		});
	});

	it('restores a persisted sort with the first request', async () => {
		localStorage.setItem(
			FILTERS_KEY,
			JSON.stringify({
				status: 'open',
				labels: [],
				workFilter: 'frontier',
				search: '',
				sortField: 'title',
				direction: 'asc',
				childDirection: 'desc'
			})
		);
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: [openIssue] }));
		vi.stubGlobal('fetch', fetchMock);

		render(IssueList, { props: { labels } });

		expect(await screen.findByText('Quarkus MCP research')).toBeInTheDocument();
		const url = lastFetchUrl(fetchMock);
		expect(url).toContain('sort=title');
		expect(url).toContain('direction=asc');
		expect(url).toContain('child_direction=desc');
	});

	it('falls back to the default frontier view when the stored snapshot carries the legacy frontier boolean', async () => {
		// A chip-era snapshot is not migrated: it fails validation and the
		// default frontier view takes over — the one-time silent reset the
		// design accepts.
		localStorage.setItem(
			FILTERS_KEY,
			JSON.stringify({ status: 'closed', labels: [], frontier: false, search: 'quarkus' })
		);
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: [openIssue] }));
		vi.stubGlobal('fetch', fetchMock);

		render(IssueList, { props: { labels } });

		expect(await screen.findByText('Quarkus MCP research')).toBeInTheDocument();
		const url = lastFetchUrl(fetchMock);
		expect(url).toContain('status=open');
		expect(url).toContain('has_assignee=false');
		expect(url).toContain('sort=createdAt');
		expect(url).toContain('direction=desc');
		expect(url).toContain('child_direction=asc');
		expect(screen.getByRole('radio', { name: 'Frontier' })).toBeChecked();
	});

	it('persists sort choices alongside the filters', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: [openIssue] }));
		vi.stubGlobal('fetch', fetchMock);

		render(IssueList, { props: { labels } });
		await screen.findByText('Quarkus MCP research');

		await fireEvent.click(screen.getByRole('button', { name: /sort/i }));
		await fireEvent.click(await screen.findByRole('menuitemradio', { name: 'Title' }));
		await fireEvent.click(screen.getByRole('radio', { name: 'Newest first' }));

		await waitFor(() => {
			expect(lastFetchUrl(fetchMock)).toContain('child_direction=desc');
		});
		expect(storedSnapshot()).toEqual({
			status: 'open',
			labels: [],
			workFilter: 'frontier',
			search: '',
			sortField: 'title',
			direction: 'desc',
			childDirection: 'desc'
		});
	});

	it('applies the sort defaults and overwrites the snapshot on reset', async () => {
		localStorage.setItem(
			FILTERS_KEY,
			JSON.stringify({
				status: 'open',
				labels: [],
				workFilter: 'frontier',
				search: '',
				sortField: 'title',
				direction: 'asc',
				childDirection: 'desc'
			})
		);
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: [openIssue] }));
		vi.stubGlobal('fetch', fetchMock);

		render(IssueList, { props: { labels } });
		await screen.findByText('Quarkus MCP research');
		expect(screen.getByRole('button', { name: /reset/i })).toBeEnabled();

		await fireEvent.click(screen.getByRole('button', { name: /reset/i }));

		expect(storedSnapshot()).toEqual({
			status: 'open',
			labels: [],
			workFilter: 'frontier',
			search: '',
			sortField: 'createdAt',
			direction: 'desc',
			childDirection: 'asc'
		});
		await waitFor(() => {
			const url = lastFetchUrl(fetchMock);
			expect(url).toContain('sort=createdAt');
			expect(url).toContain('direction=desc');
			expect(url).toContain('child_direction=asc');
		});
		expect(screen.getByRole('button', { name: /reset/i })).toBeDisabled();
	});

	it('renders the claimable-empty state for the frontier view', async () => {
		vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ issues: [] })));
		render(IssueList, { props: { labels } });

		expect(await screen.findByText('No issues found')).toBeInTheDocument();
		expect(
			screen.getByText(/nothing on the frontier right now/i)
		).toBeInTheDocument();
	});

	it('renders the claimed-empty state for the In progress view', async () => {
		vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ issues: [] })));
		render(IssueList, { props: { labels } });
		await screen.findByText('No issues found');

		await fireEvent.click(screen.getByRole('radio', { name: 'In progress' }));

		expect(
			await screen.findByText(/nothing in progress right now/i)
		).toBeInTheDocument();
	});

	it('renders the generic empty state when the work state is deselected', async () => {
		vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ issues: [] })));
		render(IssueList, { props: { labels } });
		await screen.findByText('No issues found');

		await fireEvent.click(screen.getByRole('radio', { name: 'Frontier' }));

		expect(await screen.findByText(/no issues match these filters/i)).toBeInTheDocument();
	});

	it('renders an error alert and a retry button on failure', async () => {
		vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('boom')));
		render(IssueList, { props: { labels } });

		expect(await screen.findByText('Could not load issues')).toBeInTheDocument();
		expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument();
	});

	it('debounces search into a search= query param', async () => {
		vi.useFakeTimers();
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: [openIssue] }));
		vi.stubGlobal('fetch', fetchMock);

		render(IssueList, { props: { labels } });
		await vi.advanceTimersByTimeAsync(0);

		const input = screen.getByRole('searchbox');
		await fireEvent.input(input, { target: { value: 'quarkus' } });
		await vi.advanceTimersByTimeAsync(300);

		expect(lastFetchUrl(fetchMock)).toContain('search=quarkus');
	});

	function makeIssues(count: number): IssueListItem[] {
		return Array.from({ length: count }, (_, i) => ({
			id: i + 1,
			title: `Issue ${i + 1}`,
			status: 'open' as const,
			assignee: null,
			labels: [],
			parentId: null,
			updatedAt: new Date().toISOString(),
			childCounts: { open: 0, closed: 0, total: 0 },
			matchesFilter: true,
			matchingChildCount: 0,
			children: []
		}));
	}

	it('peeks one extra row and shows Next when a further page exists', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: makeIssues(51) }));
		vi.stubGlobal('fetch', fetchMock);

		render(IssueList, { props: { labels } });

		expect(await screen.findByText('Issue 1')).toBeInTheDocument();
		expect(lastFetchUrl(fetchMock)).toContain('limit=51');
		// 51 returned, 50 shown; the extra row was trimmed.
		expect(screen.queryByText('Issue 51')).not.toBeInTheDocument();
		expect(screen.getByRole('button', { name: /next/i })).not.toBeDisabled();
	});

	it('hides Next when the tail returns exactly one page (no phantom Next)', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: makeIssues(50) }));
		vi.stubGlobal('fetch', fetchMock);

		render(IssueList, { props: { labels } });

		expect(await screen.findByText('Issue 1')).toBeInTheDocument();
		expect(screen.queryByRole('button', { name: /next/i })).not.toBeInTheDocument();
	});

	it('renders a root with its children grouped beneath it, dimming a container root', async () => {
		const container: IssueListItem = {
			...openIssue,
			id: 9,
			title: 'Parent issue',
			matchesFilter: false,
			matchingChildCount: 1,
			children: [
				{
					id: 12,
					title: 'Child issue',
					status: 'open',
					assignee: null,
					labels: [],
					parentId: 9,
					updatedAt: new Date().toISOString()
				}
			]
		};
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: [container] }));
		vi.stubGlobal('fetch', fetchMock);

		render(IssueList, { props: { labels } });

		expect(await screen.findByText('Parent issue')).toBeInTheDocument();
		// The closed-parent root is dimmed; its matching child is not.
		expect(screen.getByRole('link', { name: /parent issue/i })).toHaveClass('opacity-60');
		const childRow = screen.getByRole('link', { name: /#12 Child issue/i });
		expect(childRow).toHaveClass('pl-14');
		expect(childRow).not.toHaveClass('opacity-60');
	});

	it('restores the persisted filters for the first request after load', async () => {
		localStorage.setItem(
			FILTERS_KEY,
			JSON.stringify({
				status: 'closed',
				labels: ['needs-triage'],
				workFilter: undefined,
				search: 'quarkus',
				sortField: 'createdAt',
				direction: 'desc',
				childDirection: 'asc'
			})
		);
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: [closedIssue] }));
		vi.stubGlobal('fetch', fetchMock);

		render(IssueList, { props: { labels } });

		expect(await screen.findByText('Bootstrap the tracker')).toBeInTheDocument();
		// The very first request already carries the restored filters — no
		// default view flashed and then re-fetched.
		expect(fetchMock).toHaveBeenCalledTimes(1);
		const url = lastFetchUrl(fetchMock);
		expect(url).toContain('status=closed');
		expect(url).toContain('label=needs-triage');
		expect(url).toContain('search=quarkus');
		expect(url).not.toContain('has_assignee');
		expect(url).not.toContain('has_open_dependency');
		expect(screen.getByRole('radio', { name: 'Frontier' })).not.toBeChecked();
		expect(screen.getByRole('searchbox')).toHaveValue('quarkus');
	});

	it('falls back to the default frontier view when the stored blob is corrupt', async () => {
		localStorage.setItem(FILTERS_KEY, '{"status": "closed", "labels"');
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: [openIssue] }));
		vi.stubGlobal('fetch', fetchMock);

		render(IssueList, { props: { labels } });

		expect(await screen.findByText('Quarkus MCP research')).toBeInTheDocument();
		const url = lastFetchUrl(fetchMock);
		expect(url).toContain('status=open');
		expect(url).toContain('has_assignee=false');
		expect(url).toContain('has_open_dependency=false');
		expect(screen.getByRole('radio', { name: 'Frontier' })).toBeChecked();
	});

	it('falls back to the default frontier view when the stored blob has a wrong-typed key', async () => {
		localStorage.setItem(
			FILTERS_KEY,
			JSON.stringify({ status: 'closed', labels: 'needs-triage', workFilter: undefined, search: '' })
		);
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: [openIssue] }));
		vi.stubGlobal('fetch', fetchMock);

		render(IssueList, { props: { labels } });

		expect(await screen.findByText('Quarkus MCP research')).toBeInTheDocument();
		const url = lastFetchUrl(fetchMock);
		expect(url).toContain('status=open');
		expect(url).toContain('has_assignee=false');
		expect(url).toContain('has_open_dependency=false');
	});

	it('falls back to the default frontier view when the stored blob has a missing key', async () => {
		localStorage.setItem(FILTERS_KEY, JSON.stringify({ status: 'closed' }));
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: [openIssue] }));
		vi.stubGlobal('fetch', fetchMock);

		render(IssueList, { props: { labels } });

		expect(await screen.findByText('Quarkus MCP research')).toBeInTheDocument();
		const url = lastFetchUrl(fetchMock);
		expect(url).toContain('status=open');
		expect(url).toContain('has_assignee=false');
		expect(url).toContain('has_open_dependency=false');
	});

	it('restores a valid snapshot but keeps pagination at its defaults', async () => {
		localStorage.setItem(
			FILTERS_KEY,
			JSON.stringify({
				status: 'closed',
				labels: [],
				workFilter: undefined,
				search: '',
				sortField: 'createdAt',
				direction: 'desc',
				childDirection: 'asc',
				page: 3,
				pageSize: 10
			})
		);
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: [closedIssue] }));
		vi.stubGlobal('fetch', fetchMock);

		render(IssueList, { props: { labels } });

		expect(await screen.findByText('Bootstrap the tracker')).toBeInTheDocument();
		const url = lastFetchUrl(fetchMock);
		expect(url).toContain('status=closed');
		expect(url).toContain('offset=0');
		expect(url).toContain('limit=51');
	});

	it('persists filter changes to storage', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: [openIssue] }));
		vi.stubGlobal('fetch', fetchMock);

		render(IssueList, { props: { labels } });
		await screen.findByText('Quarkus MCP research');

		await fireEvent.click(screen.getByRole('radio', { name: 'Frontier' }));
		await fireEvent.click(screen.getByRole('radio', { name: /closed/i }));
		await fireEvent.click(screen.getByRole('button', { name: /labels/i }));
		await fireEvent.click(await screen.findByRole('checkbox', { name: /needs-triage/i }));

		await waitFor(() => {
			expect(lastFetchUrl(fetchMock)).toContain('label=needs-triage');
		});
		expect(storedSnapshot()).toEqual({
			status: 'closed',
			labels: ['needs-triage'],
			workFilter: undefined,
			search: '',
			sortField: 'createdAt',
			direction: 'desc',
			childDirection: 'asc'
		});
	});

	it('persists search text only after the 300ms debounce', async () => {
		vi.useFakeTimers();
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: [openIssue] }));
		vi.stubGlobal('fetch', fetchMock);

		render(IssueList, { props: { labels } });
		await vi.advanceTimersByTimeAsync(0);

		await fireEvent.input(screen.getByRole('searchbox'), { target: { value: 'quarkus' } });
		// The debounce has not fired yet, so storage still holds the applied search.
		expect(storedSnapshot()).toMatchObject({ search: '' });

		await vi.advanceTimersByTimeAsync(300);
		expect(storedSnapshot()).toEqual({
			status: 'open',
			labels: [],
			workFilter: 'frontier',
			search: 'quarkus',
			sortField: 'createdAt',
			direction: 'desc',
			childDirection: 'asc'
		});
	});

	it('does not persist pagination to storage', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: makeIssues(51) }));
		vi.stubGlobal('fetch', fetchMock);

		render(IssueList, { props: { labels } });
		await screen.findByText('Issue 1');

		await fireEvent.click(screen.getByRole('button', { name: /next/i }));
		await waitFor(() => {
			expect(lastFetchUrl(fetchMock)).toContain('offset=50');
		});

		expect(storedSnapshot()).toEqual({
			status: 'open',
			labels: [],
			workFilter: 'frontier',
			search: '',
			sortField: 'createdAt',
			direction: 'desc',
			childDirection: 'asc'
		});
	});

	it('applies the default frontier view and overwrites the persisted snapshot on reset', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: [openIssue] }));
		vi.stubGlobal('fetch', fetchMock);

		render(IssueList, { props: { labels } });
		await screen.findByText('Quarkus MCP research');

		await fireEvent.click(screen.getByRole('radio', { name: 'Frontier' }));
		await fireEvent.click(screen.getByRole('radio', { name: /closed/i }));
		await fireEvent.click(screen.getByRole('button', { name: /labels/i }));
		await fireEvent.click(await screen.findByRole('checkbox', { name: /needs-triage/i }));
		await waitFor(() => {
			expect(lastFetchUrl(fetchMock)).toContain('label=needs-triage');
		});
		expect(storedSnapshot()).toEqual({
			status: 'closed',
			labels: ['needs-triage'],
			workFilter: undefined,
			search: '',
			sortField: 'createdAt',
			direction: 'desc',
			childDirection: 'asc'
		});
		expect(screen.getByRole('button', { name: /reset/i })).toBeEnabled();

		await fireEvent.click(screen.getByRole('button', { name: /reset/i }));

		// The defaults are written over the stored snapshot, not merged into it.
		expect(storedSnapshot()).toEqual({
			status: 'open',
			labels: [],
			workFilter: 'frontier',
			search: '',
			sortField: 'createdAt',
			direction: 'desc',
			childDirection: 'asc'
		});
		expect(screen.getByRole('radio', { name: 'Frontier' })).toBeChecked();
		expect(screen.getByRole('searchbox')).toHaveValue('');
		expect(screen.getByRole('button', { name: /reset/i })).toBeDisabled();
		await waitFor(() => {
			const url = lastFetchUrl(fetchMock);
			expect(url).toContain('status=open');
			expect(url).toContain('has_assignee=false');
			expect(url).toContain('has_open_dependency=false');
			expect(url).not.toContain('label=');
			expect(url).not.toContain('search=');
			expect(url).toContain('offset=0');
		});
	});

	it('returns to page 1 on reset', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: makeIssues(51) }));
		vi.stubGlobal('fetch', fetchMock);

		render(IssueList, { props: { labels } });
		await screen.findByText('Issue 1');

		// Page 2 alone never enables the reset; a filter has to diverge too.
		await fireEvent.click(screen.getByRole('radio', { name: 'Frontier' }));
		await fireEvent.click(screen.getByRole('button', { name: /next/i }));
		await waitFor(() => {
			expect(lastFetchUrl(fetchMock)).toContain('offset=50');
		});

		await fireEvent.click(screen.getByRole('button', { name: /reset/i }));

		await waitFor(() => {
			expect(lastFetchUrl(fetchMock)).toContain('offset=0');
		});
	});

	it('clears an applied search term from the box and storage on reset', async () => {
		vi.useFakeTimers();
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: [openIssue] }));
		vi.stubGlobal('fetch', fetchMock);

		render(IssueList, { props: { labels } });
		await vi.advanceTimersByTimeAsync(0);

		await fireEvent.input(screen.getByRole('searchbox'), { target: { value: 'quarkus' } });
		await vi.advanceTimersByTimeAsync(300);
		expect(storedSnapshot()).toEqual({
			status: 'open',
			labels: [],
			workFilter: 'frontier',
			search: 'quarkus',
			sortField: 'createdAt',
			direction: 'desc',
			childDirection: 'asc'
		});
		expect(screen.getByRole('button', { name: /reset/i })).toBeEnabled();

		await fireEvent.click(screen.getByRole('button', { name: /reset/i }));

		expect(screen.getByRole('searchbox')).toHaveValue('');
		expect(storedSnapshot()).toEqual({
			status: 'open',
			labels: [],
			workFilter: 'frontier',
			search: '',
			sortField: 'createdAt',
			direction: 'desc',
			childDirection: 'asc'
		});
		expect(lastFetchUrl(fetchMock)).not.toContain('search=');
	});

	it('keeps the reset disabled when only pagination has moved', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: makeIssues(51) }));
		vi.stubGlobal('fetch', fetchMock);

		render(IssueList, { props: { labels } });
		await screen.findByText('Issue 1');

		await fireEvent.click(screen.getByRole('button', { name: /next/i }));
		await waitFor(() => {
			expect(lastFetchUrl(fetchMock)).toContain('offset=50');
		});

		// Pagination is not a filter: the reset stays disabled.
		expect(screen.getByRole('button', { name: /reset/i })).toBeDisabled();
	});

	it('a reset supersedes a keystroke still waiting to debounce', async () => {
		vi.useFakeTimers();
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: [openIssue] }));
		vi.stubGlobal('fetch', fetchMock);

		render(IssueList, { props: { labels } });
		await vi.advanceTimersByTimeAsync(0);

		// Diverge by deselecting the Frontier segment, then reset before the
		// debounce fires.
		await fireEvent.click(screen.getByRole('radio', { name: 'Frontier' }));
		await fireEvent.input(screen.getByRole('searchbox'), { target: { value: 'quarkus' } });
		await fireEvent.click(screen.getByRole('button', { name: /reset/i }));

		expect(screen.getByRole('searchbox')).toHaveValue('');

		await vi.advanceTimersByTimeAsync(300);
		// The late keystroke must not re-apply after the reset.
		expect(storedSnapshot()).toEqual({
			status: 'open',
			labels: [],
			workFilter: 'frontier',
			search: '',
			sortField: 'createdAt',
			direction: 'desc',
			childDirection: 'asc'
		});
		expect(lastFetchUrl(fetchMock)).not.toContain('search=');
	});

	it('keeps the default view on a refresh after the reset', async () => {
		localStorage.setItem(
			FILTERS_KEY,
			JSON.stringify({
				status: 'closed',
				labels: ['needs-triage'],
				workFilter: undefined,
				search: 'quarkus',
				sortField: 'createdAt',
				direction: 'desc',
				childDirection: 'asc'
			})
		);
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: [closedIssue] }));
		vi.stubGlobal('fetch', fetchMock);

		const rendered = render(IssueList, { props: { labels } });
		expect(await screen.findByText('Bootstrap the tracker')).toBeInTheDocument();
		expect(screen.getByRole('button', { name: /reset/i })).toBeEnabled();

		await fireEvent.click(screen.getByRole('button', { name: /reset/i }));
		expect(storedSnapshot()).toEqual({
			status: 'open',
			labels: [],
			workFilter: 'frontier',
			search: '',
			sortField: 'createdAt',
			direction: 'desc',
			childDirection: 'asc'
		});

		// A subsequent visit hydrates from the overwritten snapshot.
		rendered.unmount();
		const refetch = vi.fn().mockResolvedValue(jsonResponse({ issues: [openIssue] }));
		vi.stubGlobal('fetch', refetch);
		render(IssueList, { props: { labels } });

		expect(await screen.findByText('Quarkus MCP research')).toBeInTheDocument();
		expect(refetch).toHaveBeenCalledTimes(1);
		const url = lastFetchUrl(refetch);
		expect(url).toContain('status=open');
		expect(url).toContain('has_assignee=false');
		expect(url).toContain('has_open_dependency=false');
		expect(screen.getByRole('button', { name: /reset/i })).toBeDisabled();
	});
});

// Minimal EventSource fake: records what the store subscribed to so a test
// can push frames and (re)connects through the real store → list wiring.
class FakeEventSource {
	url: string;
	onopen: (() => void) | null = null;
	#listeners = new Map<string, Array<(event: MessageEvent) => void>>();

	constructor(url: string) {
		this.url = url;
	}

	addEventListener(type: string, listener: (event: MessageEvent) => void): void {
		const listeners = this.#listeners.get(type) ?? [];
		listeners.push(listener);
		this.#listeners.set(type, listeners);
	}

	open(): void {
		this.onopen?.();
	}

	emit(type: string, payload: unknown): void {
		for (const listener of this.#listeners.get(type) ?? []) {
			listener({ data: JSON.stringify(payload) } as MessageEvent);
		}
	}
}

const createdIssue: IssueListItem = {
	id: 99,
	title: 'Created elsewhere',
	status: 'open',
	assignee: null,
	labels: [],
	parentId: null,
	updatedAt: new Date().toISOString(),
	childCounts: { open: 0, closed: 0, total: 0 },
	matchesFilter: true,
	matchingChildCount: 0,
	children: []
};

describe('IssueList live refresh', () => {
	// Frames flow through the real store → list wiring: the singleton store
	// connects to this fake once for the file (connect is idempotent), and
	// tests emit named frames at it like the SSE wire would.
	const source = new FakeEventSource('/api/events');
	issueEvents.connect(() => source);

	beforeEach(() => {
		vi.useFakeTimers();
		localStorage.clear();
	});
	afterEach(() => {
		vi.unstubAllGlobals();
		vi.useRealTimers();
	});

	function frame(type: IssueChangeType, issueIds: number[]): IssueChangeEvent {
		return { type, seq: 1, issueIds, at: '2026-09-13T10:00:00Z' };
	}

	it('refreshes once when the debounce window closes, and not before', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: [openIssue] }));
		vi.stubGlobal('fetch', fetchMock);

		render(IssueList, { props: { labels } });
		await vi.advanceTimersByTimeAsync(0);
		expect(fetchMock).toHaveBeenCalledTimes(1); // the mount fetch alone

		source.emit('issue_updated', frame('issue_updated', [7]));

		await vi.advanceTimersByTimeAsync(299);
		// Trailing edge: inside the window nothing has fired yet.
		expect(fetchMock).toHaveBeenCalledTimes(1);

		await vi.advanceTimersByTimeAsync(1);
		expect(fetchMock).toHaveBeenCalledTimes(2);

		await vi.advanceTimersByTimeAsync(1000);
		// Quiet after the burst stays quiet — no stray extra fetches.
		expect(fetchMock).toHaveBeenCalledTimes(2);
	});

	it('collapses a burst of frames and a reconnect into one refetch', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: [openIssue] }));
		vi.stubGlobal('fetch', fetchMock);

		render(IssueList, { props: { labels } });
		await vi.advanceTimersByTimeAsync(0);

		// An agent closing five issues in a second: every event type matters to
		// the list, and a reconnect landing mid-burst joins the same window.
		source.emit('issue_created', frame('issue_created', [10]));
		await vi.advanceTimersByTimeAsync(100);
		source.emit('issue_updated', frame('issue_updated', [11]));
		source.open();
		source.emit('issue_updated', frame('issue_updated', [12]));
		source.emit('issue_updated', frame('issue_updated', [13]));
		source.emit('issue_deleted', frame('issue_deleted', [14]));

		await vi.advanceTimersByTimeAsync(300);
		expect(fetchMock).toHaveBeenCalledTimes(2);

		await vi.advanceTimersByTimeAsync(1000);
		expect(fetchMock).toHaveBeenCalledTimes(2);
	});

	it('refreshes with the active filters the user chose, preserved', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: [openIssue] }));
		vi.stubGlobal('fetch', fetchMock);

		render(IssueList, { props: { labels } });
		await vi.advanceTimersByTimeAsync(0);

		await fireEvent.click(screen.getByRole('radio', { name: 'Frontier' }));
		await vi.advanceTimersByTimeAsync(0);
		const filterFetchCount = fetchMock.mock.calls.length;

		source.emit('issue_updated', frame('issue_updated', [7]));
		await vi.advanceTimersByTimeAsync(300);

		expect(fetchMock).toHaveBeenCalledTimes(filterFetchCount + 1);
		// The refetch reflects the user's own frontier-off choice, not a reset
		// to the default frontier view (which would carry has_assignee again).
		const url = lastFetchUrl(fetchMock);
		expect(url).toContain('status=open');
		expect(url).not.toContain('has_assignee');
		expect(url).not.toContain('has_open_dependency');
	});

	it('refreshes once per stream (re)connect so missed events self-heal', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: [openIssue] }));
		vi.stubGlobal('fetch', fetchMock);

		render(IssueList, { props: { labels } });
		await vi.advanceTimersByTimeAsync(0);
		expect(fetchMock).toHaveBeenCalledTimes(1);

		source.open();
		await vi.advanceTimersByTimeAsync(300);
		expect(fetchMock).toHaveBeenCalledTimes(2);

		source.open();
		await vi.advanceTimersByTimeAsync(300);
		// Each spaced reconnect schedules its own catch-up refetch.
		expect(fetchMock).toHaveBeenCalledTimes(3);
	});

	it('carries the flipped child order on the overflow link to the detail page', async () => {
		// A flipped embed keeps the newest ten; the "...and N more" link must
		// land the detail page at that same end of the child list.
		const container: IssueListItem = {
			...openIssue,
			id: 9,
			title: 'Parent issue',
			matchingChildCount: 12,
			children: Array.from({ length: 10 }, (_, i) => ({
				id: 20 + i,
				title: `Child ${20 + i}`,
				status: 'open' as const,
				assignee: null,
				labels: [],
				parentId: 9,
				updatedAt: new Date().toISOString()
			}))
		};
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: [container] }));
		vi.stubGlobal('fetch', fetchMock);

		render(IssueList, { props: { labels } });
		await screen.findByText('Parent issue');
		expect(screen.getByRole('link', { name: /and 2 more/i })).toHaveAttribute(
			'href',
			'/issues/9'
		);

		await fireEvent.click(screen.getByRole('radio', { name: 'Newest first' }));
		await screen.findByText('Parent issue');

		expect(screen.getByRole('link', { name: /and 2 more/i })).toHaveAttribute(
			'href',
			'/issues/9?child_direction=desc'
		);
	});

	it('keeps the toggled child direction across a live-update refresh', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: [openIssue] }));
		vi.stubGlobal('fetch', fetchMock);

		render(IssueList, { props: { labels } });
		await vi.advanceTimersByTimeAsync(0);

		await fireEvent.click(screen.getByRole('radio', { name: 'Newest first' }));
		await vi.advanceTimersByTimeAsync(0);
		expect(lastFetchUrl(fetchMock)).toContain('child_direction=desc');
		const toggleFetchCount = fetchMock.mock.calls.length;

		source.emit('issue_updated', frame('issue_updated', [7]));
		await vi.advanceTimersByTimeAsync(300);

		expect(fetchMock).toHaveBeenCalledTimes(toggleFetchCount + 1);
		// The refetch rides the user's own ordering choice, not a reset default.
		expect(lastFetchUrl(fetchMock)).toContain('child_direction=desc');
	});

	it('shows an issue created elsewhere once the debounced refetch lands', async () => {
		const fetchMock = vi
			.fn()
			.mockResolvedValueOnce(jsonResponse({ issues: [openIssue] }))
			.mockResolvedValue(jsonResponse({ issues: [openIssue, createdIssue] }));
		vi.stubGlobal('fetch', fetchMock);

		render(IssueList, { props: { labels } });
		await vi.advanceTimersByTimeAsync(0);
		expect(screen.queryByText('Created elsewhere')).not.toBeInTheDocument();

		// The MCP create reaches the list as an issue_created frame; the
		// debounced refetch is what brings the new row in — no reload.
		source.emit('issue_created', frame('issue_created', [createdIssue.id]));
		await vi.advanceTimersByTimeAsync(300);

		expect(screen.getByText('Created elsewhere')).toBeInTheDocument();
	});
});
