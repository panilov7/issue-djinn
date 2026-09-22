import { fireEvent, render, screen } from '@testing-library/svelte';
import { describe, expect, it, vi } from 'vitest';
import type { ComponentProps } from 'svelte';
import type { WorkFilter } from '$lib/types';
import IssueFilterBar from './IssueFilterBar.svelte';

// Pinned for the same reason as the work-state segments' predicate help: the
// reset's one line of help, whose copy lives here in SearchReset.
const RESET_TOOLTIP = 'Reset the filters and sort order to the default frontier view.';

type IssueFilterBarProps = ComponentProps<typeof IssueFilterBar>;

function renderBar(props: Partial<IssueFilterBarProps> = {}) {
	return render(IssueFilterBar, {
		props: {
			status: 'open',
			workFilter: 'frontier' as WorkFilter | undefined,
			labels: ['needs-triage'],
			selectedLabels: [],
			sortField: 'createdAt',
			direction: 'desc',
			childDirection: 'asc',
			...props
		}
	});
}

/** True when `a` comes before `b` in the document order of the bar. */
function precedes(a: Element, b: Element): boolean {
	return (a.compareDocumentPosition(b) & Node.DOCUMENT_POSITION_FOLLOWING) !== 0;
}

describe('IssueFilterBar', () => {
	describe('work-state control', () => {
		it('leads the bar, before the status control', () => {
			renderBar();

			const frontier = screen.getByRole('radio', { name: 'Frontier' });
			const status = screen.getByRole('radio', { name: 'All' });
			const labels = screen.getByRole('button', { name: /^labels/i });

			expect(precedes(frontier, status)).toBe(true);
			expect(precedes(status, labels)).toBe(true);
		});

		it('shows Frontier pressed at the default view', () => {
			renderBar();

			expect(screen.getByRole('radio', { name: 'Frontier' })).toBeChecked();
			expect(screen.getByRole('radio', { name: 'In progress' })).not.toBeChecked();
		});

		it('hands a picked segment to the parent', async () => {
			const onworkfilterchange = vi.fn();
			renderBar({ onworkfilterchange });

			await fireEvent.click(screen.getByRole('radio', { name: 'In progress' }));

			expect(onworkfilterchange).toHaveBeenCalledWith('inProgress');
		});

		it('hands a deselection to the parent', async () => {
			const onworkfilterchange = vi.fn();
			renderBar({ workFilter: 'frontier', onworkfilterchange });

			await fireEvent.click(screen.getByRole('radio', { name: 'Frontier' }));

			expect(onworkfilterchange).toHaveBeenCalledWith(undefined);
		});

		// The segments' predicate tooltips are the control's own contract,
		// pinned by WorkStateToggleGroup's test — the bar only places the
		// control, so its own tests stop at selection and interplay.
	});

	describe('status interplay', () => {
		it('disables the status control while a work-state segment is selected', () => {
			renderBar({ workFilter: 'frontier' });

			const statuses = screen.getAllByRole('radio', { name: /^(All|Open|Closed)$/ });
			for (const status of statuses) expect(status).toBeDisabled();
		});

		it('pins the status control to Open while a work-state segment is selected', () => {
			renderBar({ workFilter: 'inProgress', status: 'open' });

			expect(screen.getByRole('radio', { name: 'Open' })).toBeChecked();
		});

		it('re-enables the status control when the work-state control is deselected', () => {
			renderBar({ workFilter: undefined, status: 'open' });

			const statuses = screen.getAllByRole('radio', { name: /^(All|Open|Closed)$/ });
			for (const status of statuses) expect(status).toBeEnabled();
			// The status keeps its value across the deselect — the parent leaves
			// it untouched, editable again.
			expect(screen.getByRole('radio', { name: 'Open' })).toBeChecked();
		});
	});

	describe('control order', () => {
		it('runs work state, status, labels, search, reset on the filter row — reset last', () => {
			renderBar();

			const workState = screen.getByRole('group', { name: 'Work state' });
			const status = screen.getByRole('radio', { name: 'All' });
			const labels = screen.getByRole('button', { name: /^labels/i });
			const search = screen.getByRole('searchbox');
			const reset = screen.getByRole('button', { name: /reset/i });

			expect(precedes(workState, status)).toBe(true);
			expect(precedes(status, labels)).toBe(true);
			// Search and reset close the filter row; sorting lives below it.
			expect(precedes(labels, search)).toBe(true);
			expect(precedes(search, reset)).toBe(true);

			// "Far-right" is delivered by the `ml-auto` wrapper, and jsdom has no
			// layout to measure — so pin the class that produces the alignment.
			expect(search.closest('.ml-auto')).not.toBeNull();
			expect(reset.closest('.ml-auto')).not.toBeNull();
		});

		it('keeps the sort controls on their own row below the filter row', () => {
			renderBar();

			const sort = screen.getByRole('button', { name: /sort/i });
			const parentOrder = screen.getByRole('button', { name: /parent issue order/i });
			const childOrder = screen.getByRole('group', { name: /child issue order/i });
			const reset = screen.getByRole('button', { name: /reset/i });

			// Sorting comes after everything on the filter row, direction toggle
			// beside the menu, child toggle beside the parent controls.
			expect(precedes(reset, sort)).toBe(true);
			expect(precedes(sort, parentOrder)).toBe(true);
			expect(precedes(parentOrder, childOrder)).toBe(true);
		});

		it('captions what each sort control targets: parent rows or child rows', () => {
			renderBar();

			const parentCaption = screen.getByText('Parent issues');
			const childCaption = screen.getByText('Child issues');
			const sort = screen.getByRole('button', { name: /sort/i });
			const childOrder = screen.getByRole('group', { name: /child issue order/i });

			// Each caption sits above the controls it names — the parent caption
			// above menu + direction toggle, the child caption above its toggle.
			expect(precedes(parentCaption, sort)).toBe(true);
			expect(precedes(childCaption, childOrder)).toBe(true);
		});
	});

	describe('parent sort controls', () => {
		it('hands a picked sort field to the parent', async () => {
			const onsortfieldchange = vi.fn();
			renderBar({ onsortfieldchange });

			await fireEvent.click(screen.getByRole('button', { name: /sort/i }));
			await fireEvent.click(await screen.findByRole('menuitemradio', { name: 'Title' }));

			expect(onsortfieldchange).toHaveBeenCalledWith('title');
		});

		it('hands a flipped sort direction to the parent from the compact flip button', async () => {
			const ondirectionchange = vi.fn();
			renderBar({ ondirectionchange });

			// No menu detour anymore: the flip button sits beside the Sort menu
			// on the sorting row, and every click reverses it.
			await fireEvent.click(screen.getByRole('button', { name: /parent issue order/i }));

			expect(ondirectionchange).toHaveBeenCalledWith('asc');
		});

		it('shows the child direction toggle in the oldest-first default state', () => {
			renderBar();

			expect(screen.getByRole('radio', { name: 'Oldest first' })).toBeChecked();
			expect(screen.getByRole('radio', { name: 'Newest first' })).not.toBeChecked();
		});

		it('hands a flipped child direction to the parent', async () => {
			const onchilddirectionchange = vi.fn();
			renderBar({ childDirection: 'asc', onchilddirectionchange });

			await fireEvent.click(screen.getByRole('radio', { name: 'Newest first' }));

			expect(onchilddirectionchange).toHaveBeenCalledWith('desc');
		});
	});

	describe('reset button', () => {
		it('is icon-only: the accessible name comes from the aria-label, with no visible text', () => {
			renderBar();

			const reset = screen.getByRole('button', { name: /reset/i });
			expect(reset).toHaveTextContent('');
		});

		it('is disabled while the filters and sort equal the default frontier view', () => {
			renderBar();
			expect(screen.getByRole('button', { name: /reset/i })).toBeDisabled();
		});

		it.each([
			['the work-state control is deselected', { workFilter: undefined }],
			['the In progress segment is selected', { workFilter: 'inProgress' as const }],
			['a label is selected', { selectedLabels: ['needs-triage'] }],
			['search text is applied', { search: 'quarkus' }],
			[
				'the parent sort diverges from the newest-created-first default',
				{ sortField: 'title' as const, direction: 'asc' as const }
			],
			[
				'the child direction diverges from the oldest-first default',
				{ childDirection: 'desc' as const }
			]
		])('is enabled once %s', (_, props) => {
			renderBar(props);
			expect(screen.getByRole('button', { name: /reset/i })).toBeEnabled();
		});

		it('asks the parent to reset when clicked', async () => {
			const onreset = vi.fn();
			// The button is disabled at the defaults, so diverge first.
			renderBar({ workFilter: undefined, onreset });

			await fireEvent.click(screen.getByRole('button', { name: /reset/i }));

			expect(onreset).toHaveBeenCalledOnce();
		});

		it('shows the reset copy when the button is focused', async () => {
			// Disabled at the defaults, so a focusable button needs a divergence.
			renderBar({ workFilter: undefined });
			expect(screen.queryByText(RESET_TOOLTIP)).not.toBeInTheDocument();

			await fireEvent.focus(screen.getByRole('button', { name: /reset/i }));

			expect(await screen.findByText(RESET_TOOLTIP)).toBeInTheDocument();
		});
	});
});