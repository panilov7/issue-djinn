import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/svelte';
import IssueEditForm from './IssueEditForm.svelte';

function makeProps(overrides: Record<string, unknown> = {}) {
	return {
		title: 'Old title',
		description: 'Old body',
		labels: ['needs-triage'],
		onSave: vi.fn(),
		onCancel: vi.fn(),
		...overrides
	};
}

describe('IssueEditForm', () => {
	it('renders the form inside a card', () => {
		render(IssueEditForm, { props: makeProps() });

		const card = document.querySelector('[data-slot="card"]');
		expect(card).toContainElement(screen.getByRole('button', { name: /save changes/i }));
	});

	it('seeds the fields from the existing issue and never offers a parent picker', () => {
		render(IssueEditForm, { props: makeProps({ labels: ['needs-triage', 'ui'] }) });

		expect(screen.getByLabelText('Title')).toHaveValue('Old title');
		expect(screen.getByLabelText('Description')).toHaveValue('Old body');
		expect(screen.getByText('needs-triage')).toBeInTheDocument();
		expect(screen.getByText('ui')).toBeInTheDocument();
		expect(screen.queryByRole('button', { name: /select parent issue/i })).not.toBeInTheDocument();
	});

	it('disables save while the title is empty', () => {
		render(IssueEditForm, { props: makeProps({ title: '' }) });
		expect(screen.getByRole('button', { name: /save changes/i })).toBeDisabled();
	});

	it('saves the edited title, description and labels', async () => {
		const p = makeProps();
		render(IssueEditForm, { props: p });
		await fireEvent.input(screen.getByLabelText('Title'), { target: { value: 'New title' } });
		await fireEvent.input(screen.getByLabelText('Description'), {
			target: { value: 'New body' }
		});
		const labelInput = screen.getByLabelText('Add label');
		await fireEvent.input(labelInput, { target: { value: 'ui' } });
		await fireEvent.keyDown(labelInput, { key: 'Enter' });

		await fireEvent.click(screen.getByRole('button', { name: /save changes/i }));

		await waitFor(() =>
			expect(p.onSave).toHaveBeenCalledWith({
				title: 'New title',
				description: 'New body',
				labels: ['needs-triage', 'ui'],
				parentId: null
			})
		);
	});

	it('saves an emptied label list so every label can be removed', async () => {
		const p = makeProps({ labels: ['needs-triage', 'ui'] });
		render(IssueEditForm, { props: p });
		await fireEvent.click(screen.getByRole('button', { name: 'Remove needs-triage' }));
		await fireEvent.click(screen.getByRole('button', { name: 'Remove ui' }));

		await fireEvent.click(screen.getByRole('button', { name: /save changes/i }));

		await waitFor(() =>
			expect(p.onSave).toHaveBeenCalledWith(expect.objectContaining({ labels: [] }))
		);
	});

	it('shows an error alert when saving fails', async () => {
		const p = makeProps({ onSave: vi.fn().mockRejectedValue(new Error('boom')) });
		render(IssueEditForm, { props: p });
		await fireEvent.click(screen.getByRole('button', { name: /save changes/i }));
		expect(await screen.findByRole('alert')).toBeInTheDocument();
		// The failed save keeps the form mounted and ready for another attempt.
		expect(screen.getByRole('button', { name: /save changes/i })).toBeEnabled();
	});

	it('calls onCancel when Cancel is clicked', async () => {
		const p = makeProps();
		render(IssueEditForm, { props: p });
		await fireEvent.click(screen.getByRole('button', { name: /cancel/i }));
		expect(p.onCancel).toHaveBeenCalledTimes(1);
	});
});