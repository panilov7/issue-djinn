import { fireEvent, render, screen } from '@testing-library/svelte';
import { describe, expect, it, vi } from 'vitest';
import StatusToggleGroup from './StatusToggleGroup.svelte';

function item(name: string) {
	return screen.getByRole('radio', { name });
}

describe('StatusToggleGroup', () => {
	it('renders All, Open and Closed with All checked for "all"', () => {
		render(StatusToggleGroup, { props: { value: 'all' } });

		expect(item('All')).toBeInTheDocument();
		expect(item('Open')).toBeInTheDocument();
		expect(item('Closed')).toBeInTheDocument();
		expect(item('All')).toBeChecked();
		expect(item('Open')).not.toBeChecked();
		expect(item('Closed')).not.toBeChecked();
	});

	it('checks the item matching a selected status', () => {
		render(StatusToggleGroup, { props: { value: 'closed' } });

		expect(item('Closed')).toBeChecked();
		expect(item('All')).not.toBeChecked();
	});

	it('emits the selected status', async () => {
		const onchange = vi.fn();
		render(StatusToggleGroup, { props: { value: 'all', onchange } });

		await fireEvent.click(item('Open'));

		expect(onchange).toHaveBeenCalledWith('open');
	});

	it('falls back to "all" when the pressed status is deselected', async () => {
		const onchange = vi.fn();
		render(StatusToggleGroup, { props: { value: 'open', onchange } });

		await fireEvent.click(item('Open'));

		expect(onchange).toHaveBeenCalledWith('all');
	});

	it('emits "all" when All is pressed', async () => {
		const onchange = vi.fn();
		render(StatusToggleGroup, { props: { value: 'open', onchange } });

		await fireEvent.click(item('All'));

		expect(onchange).toHaveBeenCalledWith('all');
	});

	it('disables every item and emits nothing when disabled', async () => {
		const onchange = vi.fn();
		render(StatusToggleGroup, { props: { value: 'open', onchange, disabled: true } });

		const items = screen.getAllByRole('radio');
		expect(items).toHaveLength(3);
		for (const entry of items) expect(entry).toBeDisabled();

		await fireEvent.click(item('Closed'));
		expect(onchange).not.toHaveBeenCalled();
	});
});