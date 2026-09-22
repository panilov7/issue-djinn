import { fireEvent, render, screen, waitFor, within } from '@testing-library/svelte';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ComponentProps } from 'svelte';
import IssuePicker from './IssuePicker.svelte';
import { listIssues } from '$lib/api/issues';
import type { IssueListItem, IssueStatus } from '$lib/types';

vi.mock('$lib/api/issues', () => ({
	listIssues: vi.fn()
}));

type IssuePickerProps = ComponentProps<typeof IssuePicker>;

function issue(id: number, title: string, status: IssueStatus = 'open'): IssueListItem {
	return {
		id,
		title,
		status,
		assignee: null,
		labels: [],
		parentId: null,
		updatedAt: new Date().toISOString(),
		childCounts: { open: 0, closed: 0, total: 0 },
		matchesFilter: true,
		matchingChildCount: 0,
		children: []
	};
}

const mockIssues = [
	issue(1, 'Bootstrap the tracker'),
	issue(2, 'Add a combobox'),
	issue(3, 'Fix the bug'),
	issue(4, 'Zebra issue')
];

function makeProps(overrides: Partial<IssuePickerProps> = {}): IssuePickerProps {
	return {
		status: 'open',
		onSelect: vi.fn(),
		...overrides
	};
}

/** Open the popover and wait for its page to render. */
async function openPicker(props: Partial<IssuePickerProps> = {}) {
	render(IssuePicker, { props: makeProps(props) });
	await fireEvent.click(screen.getByRole('button'));
	await waitFor(() => expect(listIssues).toHaveBeenCalled());
}

/**
 * Open the picker, type a search term, and let the debounce fire. Uses fake
 * timers, so the caller must set up its mock and must not waitFor before this
 * runs.
 */
async function openPickerAndSearch(term: string, props: Partial<IssuePickerProps> = {}) {
	vi.useFakeTimers({ shouldAdvanceTime: false });
	await openPicker(props);
	await fireEvent.input(screen.getByRole('combobox'), { target: { value: term } });
	vi.advanceTimersByTime(300);
}

/**
 * The rendered candidate rows, in rank order.
 *
 * `hidden: true` because the fake-timer tests leave Bits UI's popover
 * unpositioned and therefore invisible, which `getByRole` would report as
 * absent. What is under test is the order the rows render in, not visibility.
 */
function candidateRows() {
	return screen.getAllByRole('option', { hidden: true });
}

describe('IssuePicker', () => {
	afterEach(() => {
		vi.useRealTimers();
		vi.restoreAllMocks();
	});

	beforeEach(() => {
		vi.clearAllMocks();
	});

	it('renders a trigger button', () => {
		render(IssuePicker, { props: makeProps() });
		expect(screen.getByRole('button')).toBeInTheDocument();
	});

	it('forwards class and rest props to the trigger button', () => {
		render(
			IssuePicker,
			{ props: makeProps({ class: 'w-full', 'aria-label': 'Pick an issue' }) }
		);

		const trigger = screen.getByRole('button', { name: 'Pick an issue' });
		expect(trigger).toHaveClass('w-full');
	});

	describe('fetching', () => {
		it('fetches the picker status on open', async () => {
			vi.mocked(listIssues).mockResolvedValue({ issues: mockIssues });

			await openPicker({ status: 'open' });

			expect(listIssues).toHaveBeenCalledWith({ status: 'open', flat: true });
		});

		it('fetches both statuses for the "all" picker', async () => {
			vi.mocked(listIssues).mockResolvedValue({ issues: mockIssues });

			await openPicker({ status: 'all' });

			// The picker hands the filter value straight through; the API client
			// serializes 'all' as param omission, pinned in api/issues.test.ts.
			expect(listIssues).toHaveBeenCalledWith({ status: 'all', flat: true });
		});

		it('shows issue list after successful fetch', async () => {
			vi.mocked(listIssues).mockResolvedValue({ issues: mockIssues });

			await openPicker();

			await vi.waitFor(() => {
				expect(screen.getByText('Bootstrap the tracker')).toBeInTheDocument();
				expect(screen.getByText('#1')).toBeInTheDocument();
			});
		});

		it('keeps the server page order until a term is searched', async () => {
			// The server returns most-recently-updated first, and the picker does not
			// re-sort that — ranking is only for the term that produced the page.
			vi.mocked(listIssues).mockResolvedValue({
				issues: [issue(1, 'Zebra issue'), issue(2, 'Add a combobox'), issue(3, 'Alpha')]
			});

			await openPicker();

			await vi.waitFor(() => screen.getByText('Zebra issue'));
			const rows = screen.getAllByRole('option', { hidden: true });
			expect(rows.map((row) => row.textContent)).toEqual([
				expect.stringContaining('Zebra issue'),
				expect.stringContaining('Add a combobox'),
				expect.stringContaining('Alpha')
			]);
		});

		it('shows empty state when no issues match', async () => {
			vi.mocked(listIssues).mockResolvedValue({ issues: [] });

			await openPicker();

			await vi.waitFor(() => expect(screen.getByText('No issues found.')).toBeInTheDocument());
		});

		it('shows error state on fetch failure', async () => {
			vi.mocked(listIssues).mockRejectedValue(new Error('Network error'));

			await openPicker();

			// The error must be announced, not buried in Command.Empty (role="presentation").
			await vi.waitFor(() =>
				expect(screen.getByRole('alert')).toHaveTextContent('Could not load issues')
			);
		});

		it('shows a debounced skeleton only after 150ms', async () => {
			vi.useFakeTimers({ shouldAdvanceTime: false });

			let resolvePromise: (value: { issues: IssueListItem[] }) => void;
			vi.mocked(listIssues).mockImplementation(
				() =>
					new Promise<{ issues: IssueListItem[] }>((r) => {
						resolvePromise = r;
					})
			);

			render(IssuePicker, { props: makeProps() });
			await fireEvent.click(screen.getByRole('button'));

			vi.advanceTimersByTime(100);
			expect(screen.queryByTestId('loading-row')).not.toBeInTheDocument();

			vi.advanceTimersByTime(50);
			// Shaped like candidate rows, so the list does not jump when the page lands.
			await vi.waitFor(() => expect(screen.getAllByTestId('loading-row')).toHaveLength(3));

			// Let the pending fetch settle before the next test's real timers.
			vi.useRealTimers();
			await waitFor(async () => {
				resolvePromise!({ issues: [] });
			});
		});
	});

	describe('search', () => {
		it('debounces typing into the list query with the search param', async () => {
			vi.useFakeTimers({ shouldAdvanceTime: false });
			vi.mocked(listIssues).mockResolvedValue({ issues: mockIssues });

			await openPicker();
			expect(listIssues).toHaveBeenCalledTimes(1);

			await fireEvent.input(screen.getByRole('combobox'), { target: { value: 'combobox' } });
			expect(listIssues).toHaveBeenCalledTimes(1);

			vi.advanceTimersByTime(300);

			expect(listIssues).toHaveBeenLastCalledWith({ status: 'open', search: 'combobox', flat: true });
		});

		it('restores the unsearched page immediately when the query is cleared', async () => {
			vi.useFakeTimers({ shouldAdvanceTime: false });
			vi.mocked(listIssues).mockResolvedValue({ issues: mockIssues });

			await openPicker();

			await fireEvent.input(screen.getByRole('combobox'), { target: { value: 'combobox' } });
			vi.advanceTimersByTime(300);
			expect(listIssues).toHaveBeenLastCalledWith({ status: 'open', search: 'combobox', flat: true });

			await fireEvent.input(screen.getByRole('combobox'), { target: { value: '' } });
			expect(listIssues).toHaveBeenLastCalledWith({ status: 'open', flat: true });
		});

		it('sends the search term verbatim, whitespace and all', async () => {
			vi.useFakeTimers({ shouldAdvanceTime: false });
			vi.mocked(listIssues).mockResolvedValue({ issues: mockIssues });

			await openPicker();

			// The backend strips surrounding whitespace before its id parse; the UI
			// must not pre-trim and change what a text search matches (CONTEXT.md).
			await fireEvent.input(screen.getByRole('combobox'), { target: { value: ' 36 ' } });
			vi.advanceTimersByTime(300);

			expect(listIssues).toHaveBeenLastCalledWith({ status: 'open', search: ' 36 ', flat: true });
		});

		it('ranks the issue the term names by id first', async () => {
			vi.mocked(listIssues).mockResolvedValue({ issues: [issue(1, 'Alpha'), issue(36, 'Beta')] });

			await openPickerAndSearch('#36');

			await vi.waitFor(() => expect(candidateRows()[0]).toHaveTextContent('Beta'));
		});

		it('ranks title-starts-with above title-contains', async () => {
			vi.mocked(listIssues).mockResolvedValue({
				issues: [issue(1, 'Undo the label change'), issue(2, 'Label rendering')]
			});

			await openPickerAndSearch('label');

			await vi.waitFor(() =>
				expect(candidateRows()[0]).toHaveTextContent('Label rendering')
			);
		});

		it('discards a stale response once a newer search has superseded it', async () => {
			vi.useFakeTimers({ shouldAdvanceTime: false });

			let resolveStale: (value: { issues: IssueListItem[] }) => void;
			let resolveFresh: (value: { issues: IssueListItem[] }) => void;
			vi.mocked(listIssues)
				.mockImplementationOnce(
					() =>
						new Promise<{ issues: IssueListItem[] }>((r) => {
							resolveStale = r;
						})
				)
				.mockImplementationOnce(
					() =>
						new Promise<{ issues: IssueListItem[] }>((r) => {
							resolveFresh = r;
						})
				);

			render(IssuePicker, { props: makeProps() });
			await fireEvent.click(screen.getByRole('button'));
			await fireEvent.input(screen.getByRole('combobox'), { target: { value: 'combo' } });
			vi.advanceTimersByTime(300);

			await vi.waitFor(() => expect(listIssues).toHaveBeenCalledTimes(2));

			resolveStale!({ issues: [issue(1, 'Stale result')] });
			resolveFresh!({ issues: [issue(2, 'Fresh result')] });

			await vi.waitFor(() => expect(screen.getByText('Fresh result')).toBeInTheDocument());
			expect(screen.queryByText('Stale result')).not.toBeInTheDocument();
		});
	});

	describe('status filter', () => {
		it('renders no filter for a fixed status', async () => {
			vi.mocked(listIssues).mockResolvedValue({ issues: mockIssues });

			await openPicker({ status: 'open' });

			expect(screen.queryByRole('radio', { name: 'All' })).not.toBeInTheDocument();
			expect(screen.queryByRole('radio', { name: 'Closed' })).not.toBeInTheDocument();
		});

		it('renders the filter with All pressed for the "all" picker', async () => {
			vi.mocked(listIssues).mockResolvedValue({ issues: mockIssues });

			await openPicker({ status: 'all' });

			expect(screen.getByRole('radio', { name: 'All' })).toBeChecked();
			expect(screen.getByRole('radio', { name: 'Open' })).toBeInTheDocument();
			expect(screen.getByRole('radio', { name: 'Closed' })).toBeInTheDocument();
		});

		it('refetches server-side with the chosen status', async () => {
			vi.mocked(listIssues).mockResolvedValue({ issues: mockIssues });

			await openPicker({ status: 'all' });

			await fireEvent.click(screen.getByRole('radio', { name: 'Closed' }));

			expect(listIssues).toHaveBeenLastCalledWith({ status: 'closed', flat: true });
		});

		it('keeps the typed search when the status changes', async () => {
			vi.mocked(listIssues).mockResolvedValue({ issues: mockIssues });

			await openPickerAndSearch('combo', { status: 'all' });

			await fireEvent.click(screen.getByRole('radio', { name: 'Closed' }));

			expect(listIssues).toHaveBeenLastCalledWith({ status: 'closed', search: 'combo', flat: true });
		});

		it('reopens on All with an empty search', async () => {
			vi.mocked(listIssues).mockResolvedValue({ issues: mockIssues });

			const props = makeProps({ status: 'all' });
			render(IssuePicker, { props });
			const trigger = screen.getByRole('button');

			await fireEvent.click(trigger);
			await vi.waitFor(() => screen.getByText('Bootstrap the tracker'));
			await fireEvent.input(screen.getByRole('combobox'), { target: { value: 'combo' } });
			await fireEvent.click(screen.getByRole('radio', { name: 'Closed' }));

			await fireEvent.click(trigger); // close
			await fireEvent.click(trigger); // reopen

			await vi.waitFor(() =>
				expect(screen.getByRole('radio', { name: 'All' })).toBeChecked()
			);
			expect(screen.getByRole('combobox')).toHaveValue('');
			expect(listIssues).toHaveBeenLastCalledWith({ status: 'all', flat: true });
		});
	});

	describe('candidates', () => {
		it('excludes ids passed via excludeIds', async () => {
			vi.mocked(listIssues).mockResolvedValue({ issues: mockIssues });

			await openPicker({ status: 'open', excludeIds: [1, 2] });

			await vi.waitFor(() => screen.getByText('Fix the bug'));
			expect(screen.queryByText('Bootstrap the tracker')).not.toBeInTheDocument();
			expect(screen.queryByText('Add a combobox')).not.toBeInTheDocument();
		});

		it('badges every candidate row', async () => {
			vi.mocked(listIssues).mockResolvedValue({ issues: mockIssues });

			await openPicker();

			await vi.waitFor(() => screen.getByText('Bootstrap the tracker'));
			for (const row of screen.getAllByRole('option')) {
				expect(within(row).getByText('open')).toBeInTheDocument();
			}
		});

		it('badges closed candidates offered by the "all" picker', async () => {
			vi.mocked(listIssues).mockResolvedValue({
				issues: [issue(1, 'Bootstrap the tracker', 'closed')]
			});

			await openPicker({ status: 'all' });

			await vi.waitFor(() => {
				const row = screen.getByRole('option', { name: /Bootstrap the tracker/ });
				expect(within(row).getByText('closed')).toBeInTheDocument();
			});
		});

		it('calls onSelect with the selected issue', async () => {
			vi.mocked(listIssues).mockResolvedValue({ issues: mockIssues });

			const p = makeProps();
			render(IssuePicker, { props: p });
			await fireEvent.click(screen.getByRole('button'));

			await vi.waitFor(() => screen.getByText('Bootstrap the tracker'));

			await fireEvent.click(screen.getByRole('option', { name: /Bootstrap the tracker/ }));

			expect(p.onSelect).toHaveBeenCalledWith(mockIssues[0]);
		});
	});
});
