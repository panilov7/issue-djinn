import { fireEvent, render, screen } from '@testing-library/svelte';
import { describe, expect, it, vi } from 'vitest';
import Pagination from './Pagination.svelte';

describe('Pagination', () => {
	it('is hidden on the first page when there is nothing after it', () => {
		const { container } = render(Pagination, {
			props: { currentPage: 1, hasNext: false }
		});
		expect(container.querySelector('nav')).toBeNull();
	});

	it('calls onchange with the previous page', () => {
		const onchange = vi.fn();
		render(Pagination, {
			props: { currentPage: 2, hasNext: true, onchange }
		});
		fireEvent.click(screen.getByRole('button', { name: /previous/i }));
		expect(onchange).toHaveBeenCalledWith(1);
	});

	it('calls onchange with the next page', () => {
		const onchange = vi.fn();
		render(Pagination, {
			props: { currentPage: 1, hasNext: true, onchange }
		});
		fireEvent.click(screen.getByRole('button', { name: /next/i }));
		expect(onchange).toHaveBeenCalledWith(2);
	});

	it('disables prev on the first page', () => {
		render(Pagination, { props: { currentPage: 1, hasNext: true } });
		expect(screen.getByRole('button', { name: /previous/i })).toBeDisabled();
		expect(screen.getByRole('button', { name: /next/i })).not.toBeDisabled();
	});

	it('disables next when there is no next page', () => {
		render(Pagination, { props: { currentPage: 2, hasNext: false } });
		expect(screen.getByRole('button', { name: /next/i })).toBeDisabled();
	});
});
