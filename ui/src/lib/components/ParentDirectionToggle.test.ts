import { fireEvent, render, screen } from '@testing-library/svelte';
import { describe, expect, it, vi } from 'vitest';
import ParentDirectionToggle from './ParentDirectionToggle.svelte';

describe('ParentDirectionToggle', () => {
	it('is one compact flip button, ascending by default, named for the parent order', () => {
		render(ParentDirectionToggle, { props: { value: 'asc' } });

		const flip = screen.getByRole('button', { name: /parent issue order/i });
		// Short text beside the arrow keeps the sorting row narrow; the
		// accessible name spells the full state out.
		expect(flip).toHaveTextContent('asc');
		expect(flip).toHaveAccessibleName(/ascending/i);
		// jsdom has no layout to measure — pin the classes that hold the width
		// constant across the asc/desc flip, so neighbours never jump.
		expect(flip).toHaveClass('w-[3.8rem]', 'px-2');
	});

	it('reads desc with a descending name when the parent direction is desc', () => {
		render(ParentDirectionToggle, { props: { value: 'desc' } });

		const flip = screen.getByRole('button', { name: /parent issue order/i });
		expect(flip).toHaveTextContent('desc');
		expect(flip).toHaveAccessibleName(/descending/i);
	});

	it('emits the flipped direction on click', async () => {
		const onchange = vi.fn();
		render(ParentDirectionToggle, { props: { value: 'desc', onchange } });

		await fireEvent.click(screen.getByRole('button', { name: /parent issue order/i }));

		expect(onchange).toHaveBeenCalledWith('asc');
	});

	it('flips back when clicked from the other direction — every click reverses', async () => {
		// The toggle is controlled, as the child toggle is: the listing flips
		// its persisted state, and the new direction flows back through props.
		const onchange = vi.fn();
		const { unmount } = render(ParentDirectionToggle, { props: { value: 'asc', onchange } });

		await fireEvent.click(screen.getByRole('button', { name: /parent issue order/i }));
		expect(onchange).toHaveBeenCalledWith('desc');
		unmount();

		render(ParentDirectionToggle, { props: { value: 'desc', onchange } });
		await fireEvent.click(screen.getByRole('button', { name: /parent issue order/i }));
		expect(onchange).toHaveBeenLastCalledWith('asc');
	});
});