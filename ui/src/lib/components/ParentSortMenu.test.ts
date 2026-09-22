import { fireEvent, render, screen } from '@testing-library/svelte';
import { describe, expect, it, vi } from 'vitest';
import ParentSortMenu from './ParentSortMenu.svelte';

function openMenu() {
	return render(ParentSortMenu, {
		props: { sortField: 'createdAt' }
	});
}

describe('ParentSortMenu', () => {
	it('names the current field on its trigger', () => {
		openMenu();

		const trigger = screen.getByRole('button', { name: /sort/i });
		expect(trigger).toHaveTextContent('Created');
		// The trigger stays a menu trigger, not a pressed toggle.
		expect(trigger).toHaveAttribute('aria-expanded', 'false');
	});

	it('spells a title sort out on the trigger too', () => {
		render(ParentSortMenu, { props: { sortField: 'title' } });

		expect(screen.getByRole('button', { name: /sort/i })).toHaveTextContent('Title');
	});

	it('lists the four sortable fields with the current one checked', async () => {
		openMenu();

		await fireEvent.click(screen.getByRole('button', { name: /sort/i }));

		expect(await screen.findByRole('menu')).toBeInTheDocument();
		for (const field of ['Created', 'Updated', 'Title', 'Id']) {
			expect(screen.getByRole('menuitemradio', { name: field })).toBeInTheDocument();
		}
		expect(screen.getByRole('menuitemradio', { name: 'Created' })).toHaveAttribute(
			'aria-checked',
			'true'
		);
		expect(screen.getByRole('menuitemradio', { name: 'Title' })).toHaveAttribute(
			'aria-checked',
			'false'
		);
	});

	it('keeps only the checked field matching the selection', async () => {
		render(ParentSortMenu, { props: { sortField: 'updatedAt' } });

		await fireEvent.click(screen.getByRole('button', { name: /sort/i }));

		expect(await screen.findByRole('menuitemradio', { name: 'Updated' })).toHaveAttribute(
			'aria-checked',
			'true'
		);
		expect(screen.getByRole('menuitemradio', { name: 'Created' })).toHaveAttribute(
			'aria-checked',
			'false'
		);
	});

	it('emits the picked field and closes — the direction lives beside the menu now', async () => {
		const onsortfieldchange = vi.fn();
		render(ParentSortMenu, {
			props: { sortField: 'createdAt', onsortfieldchange }
		});

		await fireEvent.click(screen.getByRole('button', { name: /sort/i }));
		await fireEvent.click(await screen.findByRole('menuitemradio', { name: 'Title' }));

		expect(onsortfieldchange).toHaveBeenCalledWith('title');
		// Only fields remain in the dropdown, so a pick closes it;
		// the direction toggle is always visible beside the menu.
		expect(screen.queryByRole('menu')).not.toBeInTheDocument();
	});

	it('carries no direction toggle inside the menu', async () => {
		openMenu();

		await fireEvent.click(screen.getByRole('button', { name: /sort/i }));

		await screen.findByRole('menu');
		expect(screen.queryByRole('radio', { name: 'Ascending' })).not.toBeInTheDocument();
		expect(screen.queryByRole('radio', { name: 'Descending' })).not.toBeInTheDocument();
	});
});