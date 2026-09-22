import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/svelte';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createIssue } from '$lib/api/issues';
import IssueChildren from './IssueChildren.svelte';
import type { IssueChild, IssueDetail } from '$lib/types';

vi.mock('$lib/api/issues', () => ({ createIssue: vi.fn(), listIssues: vi.fn() }));

const goto = vi.hoisted(() => vi.fn());
vi.mock('$app/navigation', () => ({ goto }));

const toast = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock('svelte-sonner', () => ({ toast }));

/** An issue as the detail payload embeds a child. */
function child(id: number, title: string, status: 'open' | 'closed'): IssueChild {
	return {
		id,
		title,
		status,
		assignee: null,
		labels: [],
		parentId: 1,
		updatedAt: new Date().toISOString()
	};
}

const base: IssueDetail = {
	id: 1,
	title: 'Bootstrap the tracker',
	description: '',
	status: 'open',
	assignee: null,
	createdAt: new Date().toISOString(),
	updatedAt: new Date().toISOString(),
	parentId: null,
	labels: [],
	commentCount: 0,
	dependencies: [],
	dependents: [],
	children: []
};

function makeProps(overrides: Partial<IssueDetail> = {}) {
	return {
		issue: { ...base, ...overrides },
		onChildCreated: vi.fn()
	};
}

/** An issue as the create endpoint returns it. */
function createdIssue(): IssueDetail {
	return { ...base, id: 9, title: 'A new child', parentId: base.id };
}

describe('IssueChildren', () => {
	beforeEach(() => {
		vi.clearAllMocks();
	});

	it('releases the body scroll lock once the create dialog unmounts', async () => {
		// Bits UI applies a scroll lock when a dialog opens and restores the
		// body style via a real 24ms timer when the last lock is destroyed.
		// Under fake timers that timer is countable and drainable, so this pins
		// the full apply → unmount → restore cycle deterministically; the
		// vitest-setup afterAll exists because of it.
		vi.useFakeTimers();
		try {
			expect(document.body.getAttribute('style')).toBeNull();

			render(IssueChildren, { props: makeProps() });
			await fireEvent.click(screen.getByRole('button', { name: /add child issue/i }));
			await vi.advanceTimersByTimeAsync(0);
			expect(screen.getByLabelText('Title')).toBeInTheDocument();
			expect(document.body.getAttribute('style')).not.toBeNull();

			cleanup();
			await vi.advanceTimersByTimeAsync(30);
			expect(document.body.getAttribute('style')).toBe('');
		} finally {
			vi.useRealTimers();
		}
	});

	it('carries a child-direction toggle in the card header, oldest-first by default', () => {
		render(IssueChildren, { props: makeProps() });

		const group = screen.getByRole('group', { name: /child issue order/i });
		expect(group).toBeInTheDocument();
		expect(screen.getByRole('radio', { name: 'Oldest first' })).toBeChecked();
		expect(screen.getByRole('radio', { name: 'Newest first' })).not.toBeChecked();
	});

	it('shows the direction the page restored from its memento', () => {
		render(IssueChildren, { props: { issue: base, childDirection: 'desc' } });

		expect(screen.getByRole('radio', { name: 'Newest first' })).toBeChecked();
		expect(screen.getByRole('radio', { name: 'Oldest first' })).not.toBeChecked();
	});

	it('asks the page to re-order when the child direction flips', async () => {
		const onchilddirectionchange = vi.fn();
		render(IssueChildren, { props: { issue: base, onchilddirectionchange } });

		await fireEvent.click(screen.getByRole('radio', { name: 'Newest first' }));

		expect(onchilddirectionchange).toHaveBeenCalledWith('desc');
	});

	it('renders each child as a link with its id, title and status', () => {
		render(IssueChildren, {
			props: makeProps({ children: [child(5, 'Plan the work', 'open'), child(6, 'Seed the tracker', 'closed')] })
		});

		const first = screen.getByRole('link', { name: /#5 Plan the work/ });
		expect(first).toHaveAttribute('href', '/issues/5');
		expect(first).toHaveTextContent('open');
		expect(screen.getByRole('link', { name: /#6 Seed the tracker/ })).toHaveTextContent('closed');
	});

	it('shows the empty state when there are no children', () => {
		render(IssueChildren, { props: makeProps() });
		expect(screen.getByText('No child issues.')).toBeInTheDocument();
	});

	it('counts the children in the section header', () => {
		render(IssueChildren, {
			props: makeProps({ children: [child(5, 'Plan the work', 'open')] })
		});
		expect(screen.getByText(/children/i)).toHaveTextContent('1');
	});

	it('hides the add button for an issue that already has a parent', () => {
		render(IssueChildren, { props: makeProps({ parentId: 9 }) });
		expect(screen.queryByRole('button', { name: /add child issue/i })).not.toBeInTheDocument();
	});

	it('opens the create dialog with the viewed issue pre-selected as parent', async () => {
		render(IssueChildren, { props: makeProps() });

		await fireEvent.click(screen.getByRole('button', { name: /add child issue/i }));

		expect(screen.getByLabelText('Title')).toBeInTheDocument();
		expect(screen.getByRole('button', { name: /parent issue: #1 bootstrap the tracker/i })).toBeInTheDocument();
	});

	it('hands the create over to the refresh callback instead of navigating', async () => {
		const onChildCreated = vi.fn();
		vi.mocked(createIssue).mockResolvedValue(createdIssue());
		render(IssueChildren, { props: { issue: base, onChildCreated } });

		await fireEvent.click(screen.getByRole('button', { name: /add child issue/i }));
		await fireEvent.input(screen.getByLabelText('Title'), { target: { value: 'A new child' } });
		await fireEvent.click(screen.getByRole('button', { name: /create issue/i }));

		// The page invalidates its load, so the new child lands in this section
		await waitFor(() => expect(onChildCreated).toHaveBeenCalledTimes(1));
		expect(goto).not.toHaveBeenCalled();
	});
});
