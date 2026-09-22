import { fireEvent, render, screen, waitFor, within } from '@testing-library/svelte';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import IssueForm from './IssueForm.svelte';
import { listIssues } from '$lib/api/issues';
import type { IssueListItem } from '$lib/types';

vi.mock('$lib/api/issues', () => ({
	listIssues: vi.fn()
}));

const childlessCounts = { open: 0, closed: 0, total: 0 };

const openIssues: IssueListItem[] = [
	{ id: 1, title: 'Bootstrap the tracker', status: 'open', assignee: null, labels: [], parentId: null, updatedAt: new Date().toISOString(), childCounts: childlessCounts, matchesFilter: true, matchingChildCount: 0, children: [] },
	{ id: 3, title: 'Fix the bug', status: 'open', assignee: null, labels: [], parentId: null, updatedAt: new Date().toISOString(), childCounts: childlessCounts, matchesFilter: true, matchingChildCount: 0, children: [] },
	{ id: 4, title: 'Zebra issue', status: 'open', assignee: null, labels: [], parentId: null, updatedAt: new Date().toISOString(), childCounts: childlessCounts, matchesFilter: true, matchingChildCount: 0, children: [] }
];

function makeProps(overrides: Record<string, unknown> = {}) {
	return {
		onSubmit: vi.fn(),
		onCancel: vi.fn(),
		...overrides
	};
}

describe('IssueForm', () => {
	beforeEach(() => {
		vi.clearAllMocks();
	});

	afterEach(() => {
		vi.restoreAllMocks();
	});

	it('rejects an empty title, edits labels, and picks a parent before submitting', async () => {
		vi.mocked(listIssues).mockResolvedValue({ issues: openIssues });
		const p = makeProps();
		render(IssueForm, { props: p });

		// Empty title: save is disabled and the parent picker is rendered, named
		// after its field rather than after the changing "No parent" content.
		const save = screen.getByRole('button', { name: /create issue/i });
		expect(save).toBeDisabled();
		expect(screen.getByRole('button', { name: /select parent issue/i })).toBeInTheDocument();

		// Labels: add two free-form labels, then remove the first one.
		const labelInput = screen.getByLabelText('Add label');
		await fireEvent.input(labelInput, { target: { value: 'bug' } });
		await fireEvent.keyDown(labelInput, { key: 'Enter' });
		await fireEvent.input(labelInput, { target: { value: 'ready-for-agent' } });
		await fireEvent.keyDown(labelInput, { key: 'Enter' });
		expect(screen.getByText('bug')).toBeInTheDocument();

		const removeBug = screen.getByRole('button', { name: 'Remove bug' });
		await fireEvent.click(removeBug);
		expect(screen.queryByText('bug')).not.toBeInTheDocument();

		// The helper text states the contract the LabelTagInput form tests pin:
		// text that was typed but never added survives a save.
		expect(screen.getByText(/anything still typed is added on save/i)).toBeInTheDocument();

		// Parent pick, then the fields.
		await fireEvent.click(screen.getByRole('button', { name: /select parent issue/i }));
		await vi.waitFor(() => screen.getByText('Fix the bug'));
		await fireEvent.click(screen.getByRole('option', { name: /Fix the bug/ }));

		await fireEvent.input(screen.getByLabelText('Title'), { target: { value: 'New issue' } });
		await fireEvent.input(screen.getByLabelText('Description'), {
			target: { value: 'A new body' }
		});

		expect(save).toBeEnabled();
		await fireEvent.click(save);

		await waitFor(() =>
			expect(p.onSubmit).toHaveBeenCalledWith({
				title: 'New issue',
				description: 'A new body',
				labels: ['ready-for-agent'],
				parentId: 3
			})
		);
		expect(p.onCancel).not.toHaveBeenCalled();
	});

	it('shows the title error only once the field has been touched', async () => {
		render(IssueForm, { props: makeProps() });

		// Silent on mount — an announced error before any interaction is noise.
		expect(screen.queryByRole('alert')).not.toBeInTheDocument();

		await fireEvent.blur(screen.getByLabelText('Title'));
		expect(screen.getByRole('alert')).toHaveTextContent('Title is required.');
	});

	it('offers a parent from open issues only, with no status filter', async () => {
		vi.mocked(listIssues).mockResolvedValue({ issues: openIssues });
		render(IssueForm, { props: makeProps() });

		await fireEvent.click(screen.getByRole('button', { name: /select parent issue/i }));

		await vi.waitFor(() => {
			expect(listIssues).toHaveBeenCalledWith({ status: 'open', flat: true });
			expect(screen.queryByRole('radio', { name: 'All' })).not.toBeInTheDocument();
			expect(screen.queryByRole('radio', { name: 'Closed' })).not.toBeInTheDocument();
		});

		// Each candidate names its issue by number as well as title.
		const row = screen.getByRole('option', { name: /Fix the bug/ });
		expect(within(row).getByText('#3')).toBeInTheDocument();
	});

	it('clears a picked parent', async () => {
		vi.mocked(listIssues).mockResolvedValue({ issues: openIssues });
		const p = makeProps();
		render(IssueForm, { props: p });

		await fireEvent.click(screen.getByRole('button', { name: /select parent issue/i }));
		await vi.waitFor(() => screen.getByText('Fix the bug'));
		await fireEvent.click(screen.getByRole('option', { name: /Fix the bug/ }));

		const trigger = screen.getByRole('button', { name: /^parent issue: #3 fix the bug$/i });
		expect(trigger).toBeInTheDocument();

		await fireEvent.click(screen.getByRole('button', { name: /clear parent/i }));

		expect(screen.getByRole('button', { name: /select parent issue/i })).toBeInTheDocument();

		await fireEvent.input(screen.getByLabelText('Title'), { target: { value: 'New issue' } });
		await fireEvent.click(screen.getByRole('button', { name: /create issue/i }));

		await waitFor(() =>
			expect(p.onSubmit).toHaveBeenCalledWith(
				expect.objectContaining({ parentId: null })
			)
		);
	});

	it('seeds the fields from an existing issue and hides the parent picker in edit mode', async () => {
		const p = makeProps({
			mode: 'edit',
			initialTitle: 'Old title',
			initialDescription: 'Old body',
			initialLabels: ['needs-triage']
		});
		render(IssueForm, { props: p });

		expect(screen.getByLabelText('Title')).toHaveValue('Old title');
		expect(screen.getByLabelText('Description')).toHaveValue('Old body');
		expect(screen.getByText('needs-triage')).toBeInTheDocument();
		expect(screen.queryByRole('button', { name: /select parent issue/i })).not.toBeInTheDocument();
		expect(screen.getByRole('button', { name: /save changes/i })).toBeInTheDocument();

		await fireEvent.click(screen.getByRole('button', { name: /save changes/i }));
		await waitFor(() =>
			expect(p.onSubmit).toHaveBeenCalledWith(
				expect.objectContaining({ title: 'Old title', parentId: null })
			)
		);
	});

	it('seeds the parent picker from initialParent and submits it', async () => {
		vi.mocked(listIssues).mockResolvedValue({ issues: openIssues });
		const p = makeProps({ initialParent: openIssues[1] });
		render(IssueForm, { props: p });

		expect(screen.getByRole('button', { name: /parent issue: #3 fix the bug/i })).toBeInTheDocument();

		await fireEvent.input(screen.getByLabelText('Title'), { target: { value: 'Child issue' } });
		await fireEvent.click(screen.getByRole('button', { name: /create issue/i }));

		await waitFor(() => expect(p.onSubmit).toHaveBeenCalledWith(expect.objectContaining({ parentId: 3 })));
	});

	it('shows an error alert when onSubmit rejects', async () => {
		const p = makeProps({
			mode: 'edit',
			initialTitle: 'Old title',
			onSubmit: vi.fn().mockRejectedValue(new Error('boom'))
		});
		render(IssueForm, { props: p });

		await fireEvent.click(screen.getByRole('button', { name: /save changes/i }));

		// The rejection is announced and keeps the form usable.
		expect(await screen.findByRole('alert')).toHaveTextContent('boom');
		expect(screen.getByRole('button', { name: /save changes/i })).toBeEnabled();
	});

	it('calls onCancel when Cancel is clicked', async () => {
		const p = makeProps();
		render(IssueForm, { props: p });

		await fireEvent.click(screen.getByRole('button', { name: /cancel/i }));

		expect(p.onCancel).toHaveBeenCalledTimes(1);
		expect(p.onSubmit).not.toHaveBeenCalled();
	});
});
