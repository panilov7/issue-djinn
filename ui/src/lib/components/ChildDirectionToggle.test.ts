import { fireEvent, render, screen } from '@testing-library/svelte';
import { describe, expect, it, vi } from 'vitest';
import ChildDirectionToggle from './ChildDirectionToggle.svelte';

function item(name: string) {
	return screen.getByRole('radio', { name });
}

describe('ChildDirectionToggle', () => {
	it('offers oldest-first and newest-first with oldest-first checked by default', () => {
		render(ChildDirectionToggle, { props: { value: 'asc' } });

		expect(item('Oldest first')).toBeChecked();
		expect(item('Newest first')).not.toBeChecked();
		// The group is named so it reads apart from the parent sort's asc/desc
		// toggle beside the sort menu on the sorting row.
		expect(screen.getByRole('group', { name: /child issue order/i })).toBeInTheDocument();
	});

	it('checks newest-first when the child direction is desc', () => {
		render(ChildDirectionToggle, { props: { value: 'desc' } });

		expect(item('Newest first')).toBeChecked();
		expect(item('Oldest first')).not.toBeChecked();
	});

	it('emits the flipped direction', async () => {
		const onchange = vi.fn();
		render(ChildDirectionToggle, { props: { value: 'asc', onchange } });

		await fireEvent.click(item('Newest first'));

		expect(onchange).toHaveBeenCalledWith('desc');
	});

	it('emits nothing when the pressed item is clicked again', async () => {
		// bits-ui emits '' for a re-click of the pressed toggle item; a listing
		// always carries a direction, so that gesture is a no-op — not a
		// direction that would corrupt the persisted snapshot.
		const onchange = vi.fn();
		render(ChildDirectionToggle, { props: { value: 'desc', onchange } });

		await fireEvent.click(item('Newest first'));

		expect(onchange).not.toHaveBeenCalled();
	});
});