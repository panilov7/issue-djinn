import { fireEvent, render, screen } from '@testing-library/svelte';
import { describe, expect, it, vi } from 'vitest';
import LabelFilter from './LabelFilter.svelte';

describe('LabelFilter', () => {
	it('shows the selected label count on the trigger', () => {
		render(LabelFilter, {
			props: { labels: ['a', 'b'], value: ['a'] }
		});
		expect(screen.getByRole('button', { name: /labels.*\(1\)/i })).toBeInTheDocument();
	});

	it('emits the full new selection when toggling a label', async () => {
		const onchange = vi.fn();
		render(LabelFilter, {
			props: { labels: ['needs-triage', 'task'], value: [], onchange }
		});

		await fireEvent.click(screen.getByRole('button', { name: /labels/i }));
		await fireEvent.click(await screen.findByText('needs-triage'));

		expect(onchange).toHaveBeenCalledWith(['needs-triage']);
	});

	it('removes a label that is already selected', async () => {
		const onchange = vi.fn();
		render(LabelFilter, {
			props: { labels: ['a', 'b'], value: ['a', 'b'], onchange }
		});

		await fireEvent.click(screen.getByRole('button', { name: /labels.*\(2\)/i }));
		await fireEvent.click(await screen.findByText('a'));

		expect(onchange).toHaveBeenCalledWith(['b']);
	});

	it('shows an empty message when there are no labels', async () => {
		render(LabelFilter, { props: { labels: [], value: [] } });
		await fireEvent.click(screen.getByRole('button', { name: /labels/i }));
		expect(await screen.findByText('No labels yet.')).toBeInTheDocument();
	});
});
