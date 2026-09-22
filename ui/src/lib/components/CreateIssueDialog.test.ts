import { fireEvent, render, screen, waitFor } from '@testing-library/svelte';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import CreateIssueDialog from './CreateIssueDialog.svelte';
import { createIssue, listIssues } from '$lib/api/issues';
import type { IssueDetail, IssueListItem } from '$lib/types';

vi.mock('$lib/api/issues', () => ({ createIssue: vi.fn(), listIssues: vi.fn() }));

const goto = vi.hoisted(() => vi.fn());
vi.mock('$app/navigation', () => ({ goto }));

const toast = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock('svelte-sonner', () => ({ toast }));

const pickerCandidates: IssueListItem[] = [
	{ id: 3, title: 'Fix the bug', status: 'open', assignee: null, labels: [], parentId: null, updatedAt: new Date().toISOString(), childCounts: { open: 0, closed: 0, total: 0 }, matchesFilter: true, matchingChildCount: 0, children: [] }
];

const createdIssue: IssueDetail = {
	id: 42,
	title: 'New issue',
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

function openDialog() {
	return fireEvent.click(screen.getByRole('button', { name: /new issue/i }));
}

describe('CreateIssueDialog', () => {
	beforeEach(() => {
		vi.clearAllMocks();
	});

	it('opens the shared create form from the + New issue trigger', async () => {
		render(CreateIssueDialog);

		// Only the trigger is on the page until it is clicked.
		expect(screen.queryByLabelText('Title')).not.toBeInTheDocument();
		await openDialog();

		expect(screen.getByLabelText('Title')).toBeInTheDocument();
		// The picker names itself after its field, not its "No parent" content.
		expect(screen.getByRole('button', { name: /select parent issue/i })).toBeInTheDocument();
	});

	it('creates the issue with the parent and labels, then toasts, closes, and navigates', async () => {
		vi.mocked(listIssues).mockResolvedValue({ issues: pickerCandidates });
		vi.mocked(createIssue).mockResolvedValue(createdIssue);
		render(CreateIssueDialog);
		await openDialog();

		await fireEvent.click(screen.getByRole('button', { name: /select parent issue/i }));
		await vi.waitFor(() => screen.getByText('Fix the bug'));
		await fireEvent.click(screen.getByRole('option', { name: /Fix the bug/ }));

		const labelInput = screen.getByLabelText('Add label');
		await fireEvent.input(labelInput, { target: { value: 'task' } });
		await fireEvent.keyDown(labelInput, { key: 'Enter' });

		await fireEvent.input(screen.getByLabelText('Title'), { target: { value: 'New issue' } });
		await fireEvent.click(screen.getByRole('button', { name: /create issue/i }));

		await waitFor(() =>
			expect(createIssue).toHaveBeenCalledWith({
				title: 'New issue',
				description: '',
				parentId: 3,
				labels: ['task']
			})
		);
		expect(toast.success).toHaveBeenCalledWith('Issue #42 created');
		await waitFor(() => expect(goto).toHaveBeenCalledWith('/issues/42'));
	});

	it('stays open and reports the API error instead of navigating', async () => {
		vi.mocked(createIssue).mockRejectedValue(new Error('Parent issue not found: 3'));
		render(CreateIssueDialog);
		await openDialog();

		await fireEvent.input(screen.getByLabelText('Title'), { target: { value: 'New issue' } });
		await fireEvent.click(screen.getByRole('button', { name: /create issue/i }));

		expect(await screen.findByRole('alert')).toHaveTextContent('Parent issue not found: 3');
		// The rejected attempt keeps its draft so the user can retry.
		expect(screen.getByLabelText('Title')).toHaveValue('New issue');
		expect(toast.success).not.toHaveBeenCalled();
		expect(goto).not.toHaveBeenCalled();
	});

	it('starts a fresh form every time the dialog opens', async () => {
		render(CreateIssueDialog);
		await openDialog();
		await fireEvent.input(screen.getByLabelText('Title'), { target: { value: 'First attempt' } });

		await fireEvent.click(screen.getByRole('button', { name: /cancel/i }));
		expect(screen.queryByLabelText('Title')).not.toBeInTheDocument();

		await openDialog();
		expect(screen.getByLabelText('Title')).toHaveValue('');
	});

	it('pre-selects the preset parent in the form', async () => {
		render(CreateIssueDialog, { props: { presetParent: pickerCandidates[0] } });
		await openDialog();

		expect(screen.getByRole('button', { name: /parent issue: #3 fix the bug/i })).toBeInTheDocument();
	});

	it('hands the create over to onCreated instead of navigating', async () => {
		const onCreated = vi.fn();
		vi.mocked(createIssue).mockResolvedValue(createdIssue);
		render(CreateIssueDialog, { props: { presetParent: pickerCandidates[0], onCreated } });
		await openDialog();

		await fireEvent.input(screen.getByLabelText('Title'), { target: { value: 'New issue' } });
		await fireEvent.click(screen.getByRole('button', { name: /create issue/i }));

		await waitFor(() => expect(onCreated).toHaveBeenCalledTimes(1));
		// The child lands on the page that opened the dialog, not on its own page
		expect(goto).not.toHaveBeenCalled();
	});

	// Focus trap / focus-return are not asserted here: bits-ui performs them
	// on transition completion, which jsdom never fires (Element.animate is a
	// no-op in vitest-setup). The dialog stays on the plain shadcn primitives,
	// which is where that behaviour is guaranteed.
});

describe('CreateIssueDialog width', () => {
	it('scales fluidly with the viewport up to its large-screen cap', async () => {
		// The dashboard "New issue" and the Children card "Add child issue" both
		// render this component, so one width rule covers both triggers.
		render(CreateIssueDialog);
		await openDialog();

		const content = document.querySelector('[data-slot="dialog-content"]');
		// The cap is one fluid rule — viewport-relative, capped at 42rem (the
		// 2xl–3xl range) — replacing the stepped `sm:max-w-lg`, so widths grow
		// smoothly with the viewport and continuously across the sm breakpoint.
		expect(content).toHaveClass('sm:max-w-[min(calc(100%-2rem),42rem)]');
		// Small screens keep the primitive's untouched default: near-full-width
		// with 1rem gutters.
		expect(content).toHaveClass('max-w-[calc(100%-2rem)]');
		// The old stepped cap must not survive the merge — it would shrink the
		// dialog back to 32rem from the sm breakpoint up.
		expect(content).not.toHaveClass('sm:max-w-lg');
	});
});

describe('CreateIssueDialog scrolling layout', () => {
	it('caps the dialog height and scrolls the form body under a fixed header', async () => {
		render(CreateIssueDialog);
		await openDialog();

		const content = document.querySelector('[data-slot="dialog-content"]');
		if (!content) throw new Error('dialog content did not render');
		// The height cap keeps the dialog an overlay: it can never grow past
		// the viewport, however tall the form gets.
		expect(content).toHaveClass('max-h-[85vh]');
		// The content is a vertical flex column, so the header and the body are
		// independent boxes rather than one scrolling stack.
		expect(content).toHaveClass('flex', 'flex-col');

		// The form is the scrolling body. min-h-0 is what lets it shrink below
		// its content height, so overflow-y engages only when the form is
		// taller than the space left under the header; a fitting form renders
		// without a scrollbar.
		const form = document.querySelector('[data-slot="issue-form"]');
		if (!form) throw new Error('dialog form did not render');
		expect(form).toHaveClass('flex-1', 'min-h-0', 'overflow-y-auto');

		// The header block sits outside the scrolling body, so the title and
		// the close button stay visible and fixed while the body scrolls.
		const header = content.querySelector('[data-slot="dialog-header"]');
		expect(header).not.toBeNull();
		expect(form.contains(header)).toBe(false);
	});
});
