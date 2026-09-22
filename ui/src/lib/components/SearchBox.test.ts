import { fireEvent, render, screen } from '@testing-library/svelte';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import SearchBox from './SearchBox.svelte';

describe('SearchBox', () => {
	beforeEach(() => vi.useFakeTimers());
	afterEach(() => vi.useRealTimers());

	it('debounces input by 300ms before emitting onsearch', () => {
		const onsearch = vi.fn();
		render(SearchBox, { props: { onsearch } });
		const input = screen.getByRole('searchbox');

		fireEvent.input(input, { target: { value: 'quar' } });
		fireEvent.input(input, { target: { value: 'quarkus' } });

		expect(onsearch).not.toHaveBeenCalled();

		vi.advanceTimersByTime(299);
		expect(onsearch).not.toHaveBeenCalled();

		vi.advanceTimersByTime(1);
		expect(onsearch).toHaveBeenCalledTimes(1);
		expect(onsearch).toHaveBeenCalledWith('quarkus');
	});

	it('emits an empty string immediately when the box is cleared', () => {
		const onsearch = vi.fn();
		render(SearchBox, { props: { onsearch } });
		const input = screen.getByRole('searchbox');

		fireEvent.input(input, { target: { value: 'quarkus' } });
		vi.advanceTimersByTime(300);
		expect(onsearch).toHaveBeenCalledWith('quarkus');

		fireEvent.input(input, { target: { value: '' } });
		expect(onsearch).toHaveBeenLastCalledWith('');
	});

	it('clears via the clear button', () => {
		const onsearch = vi.fn();
		render(SearchBox, { props: { onsearch } });
		const input = screen.getByRole('searchbox');

		fireEvent.input(input, { target: { value: 'quarkus' } });
		vi.advanceTimersByTime(300);
		expect(onsearch).toHaveBeenCalledWith('quarkus');

		fireEvent.click(screen.getByRole('button', { name: /clear search/i }));
		expect(onsearch).toHaveBeenLastCalledWith('');
	});

	it('does not emit a keystroke still waiting to debounce after the box is destroyed', async () => {
		const onsearch = vi.fn();
		const rendered = render(SearchBox, { props: { onsearch } });
		await fireEvent.input(screen.getByRole('searchbox'), { target: { value: 'quarkus' } });

		rendered.unmount();
		await vi.advanceTimersByTime(300);

		expect(onsearch).not.toHaveBeenCalled();
	});

	it('seeds the box with the restored search text', () => {
		const onsearch = vi.fn();
		render(SearchBox, { props: { onsearch, initial: 'quarkus' } });

		expect(screen.getByRole('searchbox')).toHaveValue('quarkus');
		// Seeding is not a search: nothing is emitted.
		expect(onsearch).not.toHaveBeenCalled();
	});
});
