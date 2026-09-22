import { fireEvent, render, screen } from '@testing-library/svelte';
import { tick } from 'svelte';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import Page from './+page.svelte';
import { issueEvents } from '$lib/stores/issueEvents.svelte';
import type { IssueDetail } from '$lib/types';

// The page reacts to the stream through invalidate; goto is exported because
// child components (dependency/child pickers) import it.
const invalidate = vi.hoisted(() => vi.fn());
const goto = vi.hoisted(() => vi.fn());
vi.mock('$app/navigation', () => ({ invalidate, goto }));

// Minimal EventSource fake: records what the store subscribed to so a test
// can push frames and (re)connects through the real store → page wiring.
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

const source = new FakeEventSource('/api/events');
// The root layout owns this call in production; the test binds the fake
// through the same factory seam so frames flow through the real store.
issueEvents.connect(() => source);

const viewedIssue: IssueDetail = {
	id: 42,
	title: 'Watched issue',
	description: '',
	status: 'open',
	assignee: null,
	createdAt: '2026-09-13T09:00:00Z',
	updatedAt: '2026-09-13T09:00:00Z',
	parentId: null,
	labels: [],
	commentCount: 0,
	dependencies: [],
	dependents: [],
	children: []
};

const pageData = { id: 42, issue: viewedIssue, comments: [], childDirection: 'asc' as const };

function renderDetailPage() {
	return render(Page, { props: { data: pageData } });
}

function frame(type: string, issueIds: number[]) {
	return { type, seq: 1, issueIds, at: '2026-09-13T10:00:00Z' };
}

describe('Issue detail auto-refresh', () => {
	beforeEach(() => {
		invalidate.mockClear();
		localStorage.clear();
	});

	it('refreshes when an incoming event names the issue on screen', async () => {
		renderDetailPage();
		await tick();

		source.emit('issue_updated', frame('issue_updated', [42]));
		source.emit('issue_created', frame('issue_created', [42]));

		expect(invalidate).toHaveBeenCalledTimes(2);
		expect(invalidate).toHaveBeenCalledWith('app:issue-detail');
	});

	it('refreshes when a counterpart event names the issue on screen', async () => {
		// A dependency edit emits both ends [dependentId, dependencyId]; a
		// reparent emits [childId, oldParentId?, newParentId?] — the viewed
		// issue reacts to any of its appearances, not just as the mutated one.
		renderDetailPage();
		await tick();

		source.emit('issue_created', frame('issue_created', [43, 42]));

		expect(invalidate).toHaveBeenCalledTimes(1);
	});

	it('does not refresh on events that exclude the issue on screen', async () => {
		renderDetailPage();
		await tick();

		source.emit('issue_created', frame('issue_created', [7]));
		source.emit('issue_updated', frame('issue_updated', [7, 8]));

		expect(invalidate).not.toHaveBeenCalled();
	});

	it('invalidates once on every (re)connect so missed events self-heal', async () => {
		renderDetailPage();
		await tick();

		source.open();
		source.open();

		expect(invalidate).toHaveBeenCalledTimes(2);
		expect(invalidate).toHaveBeenCalledWith('app:issue-detail');
	});
});

describe('Child direction toggle', () => {
	const CHILD_DIRECTION_KEY = 'issue-djinn.child-direction';

	beforeEach(() => {
		invalidate.mockClear();
		localStorage.clear();
	});

	it('shows the direction the load served', async () => {
		renderDetailPage();
		await tick();

		expect(screen.getByRole('radio', { name: 'Oldest first' })).toBeChecked();
		expect(screen.getByRole('radio', { name: 'Newest first' })).not.toBeChecked();
	});

	it('shows a newest-first load the same way', async () => {
		render(Page, { props: { data: { ...pageData, childDirection: 'desc' as const } } });
		await tick();

		expect(screen.getByRole('radio', { name: 'Newest first' })).toBeChecked();
		expect(screen.getByRole('radio', { name: 'Oldest first' })).not.toBeChecked();
	});

	it('writes the flipped direction to its memento and invalidates the load', async () => {
		renderDetailPage();
		await tick();

		await fireEvent.click(screen.getByRole('radio', { name: 'Newest first' }));

		expect(JSON.parse(localStorage.getItem(CHILD_DIRECTION_KEY) as string)).toBe('desc');
		expect(invalidate).toHaveBeenCalledWith('app:issue-detail');
	});

	it('defers the re-order refresh while a form holds unsaved work, then runs it once', async () => {
		// The edit form replaces the children card entirely, so the gated
		// surface here is the comment composer — the card stays visible behind
		// it, and the gate holds while its draft is open.
		renderDetailPage();
		await tick();
		await fireEvent.click(screen.getByRole('button', { name: 'Add comment' }));

		await fireEvent.click(screen.getByRole('radio', { name: 'Newest first' }));
		await tick();
		// The choice is kept even though the refresh waits for the gate.
		expect(JSON.parse(localStorage.getItem(CHILD_DIRECTION_KEY) as string)).toBe('desc');
		expect(invalidate).not.toHaveBeenCalled();
		expect(screen.getByText('Changes available')).toBeInTheDocument();

		await fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
		await tick();

		expect(invalidate).toHaveBeenCalledTimes(1);
		expect(invalidate).toHaveBeenCalledWith('app:issue-detail');
	});
});

describe('Editing gate', () => {
	beforeEach(() => {
		invalidate.mockClear();
		localStorage.clear();
	});

	it('defers relevant events while the edit form is open and shows the indicator', async () => {
		renderDetailPage();
		await tick();
		await fireEvent.click(screen.getByRole('button', { name: 'Edit' }));

		source.emit('issue_updated', frame('issue_updated', [42]));
		source.emit('issue_updated', frame('issue_updated', [42]));
		await tick();

		expect(invalidate).not.toHaveBeenCalled();
		expect(screen.getByText('Changes available')).toBeInTheDocument();
	});

	it('keeps the typed title intact while events are deferred', async () => {
		renderDetailPage();
		await tick();
		await fireEvent.click(screen.getByRole('button', { name: 'Edit' }));

		await fireEvent.input(screen.getByLabelText('Title'), { target: { value: 'Typed by me' } });
		source.emit('issue_updated', frame('issue_updated', [42]));
		await tick();

		expect(screen.getByLabelText('Title')).toHaveValue('Typed by me');
		expect(invalidate).not.toHaveBeenCalled();
	});

	it('runs the deferred refresh exactly once when the form closes', async () => {
		renderDetailPage();
		await tick();
		await fireEvent.click(screen.getByRole('button', { name: 'Edit' }));

		source.emit('issue_updated', frame('issue_updated', [42]));
		source.emit('issue_created', frame('issue_created', [42]));
		source.emit('issue_updated', frame('issue_updated', [42]));
		await tick();

		await fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
		await tick();

		expect(invalidate).toHaveBeenCalledTimes(1);
		expect(invalidate).toHaveBeenCalledWith('app:issue-detail');
		expect(screen.queryByText('Changes available')).not.toBeInTheDocument();
	});

	it('gates the close/reopen dialog the same way', async () => {
		renderDetailPage();
		await tick();
		await fireEvent.click(screen.getByRole('button', { name: 'Close issue' }));

		source.emit('issue_updated', frame('issue_updated', [42]));
		await tick();
		expect(invalidate).not.toHaveBeenCalled();
		expect(screen.getByText('Changes available')).toBeInTheDocument();

		await fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
		await tick();
		expect(invalidate).toHaveBeenCalledTimes(1);
	});

	it('gates the comment composer the same way', async () => {
		renderDetailPage();
		await tick();
		await fireEvent.click(screen.getByRole('button', { name: 'Add comment' }));

		source.emit('issue_updated', frame('issue_updated', [42]));
		await tick();
		expect(invalidate).not.toHaveBeenCalled();
		expect(screen.getByText('Changes available')).toBeInTheDocument();

		await fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
		await tick();
		expect(invalidate).toHaveBeenCalledTimes(1);
	});

	it('gates the child-issue dialog the same way', async () => {
		renderDetailPage();
		await tick();
		await fireEvent.click(screen.getByRole('button', { name: /add child issue/i }));

		source.emit('issue_updated', frame('issue_updated', [42]));
		await tick();
		expect(invalidate).not.toHaveBeenCalled();
		expect(screen.getByText('Changes available')).toBeInTheDocument();

		await fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
		await tick();
		expect(invalidate).toHaveBeenCalledTimes(1);
	});

	it('does not light the indicator for events that exclude the viewed issue', async () => {
		renderDetailPage();
		await tick();
		await fireEvent.click(screen.getByRole('button', { name: 'Edit' }));

		source.emit('issue_created', frame('issue_created', [7]));
		await tick();

		expect(invalidate).not.toHaveBeenCalled();
		expect(screen.queryByText('Changes available')).not.toBeInTheDocument();
	});
});