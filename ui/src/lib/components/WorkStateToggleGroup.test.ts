import { fireEvent, render, screen, waitFor } from '@testing-library/svelte';
import { describe, expect, it, vi } from 'vitest';
import WorkStateToggleGroup from './WorkStateToggleGroup.svelte';

// Pinned here *and* in the component: each segment's one line of predicate
// help — the only place the UI spells out the work-state definitions. The
// frontier sentence mirrors the canonical definition in CONTEXT.md ("no
// *open* dependencies, unclaimed") and the MCP `list_frontier` description;
// both promise nothing about the order a work state is listed in.
const FRONTIER_TOOLTIP =
	'Open issues with no open dependencies and no assignee — the work you can start right now.';
const IN_PROGRESS_TOOLTIP =
	'Open issues that are claimed and unblocked — the work already happening.';

function segment(name: string) {
	return screen.getByRole('radio', { name });
}

describe('WorkStateToggleGroup', () => {
	it('renders Frontier and In progress with Frontier pressed for "frontier"', () => {
		render(WorkStateToggleGroup, { props: { value: 'frontier' } });

		expect(segment('Frontier')).toBeInTheDocument();
		expect(segment('In progress')).toBeInTheDocument();
		expect(segment('Frontier')).toBeChecked();
		expect(segment('In progress')).not.toBeChecked();
	});

	it('checks In progress for "inProgress"', () => {
		render(WorkStateToggleGroup, { props: { value: 'inProgress' } });

		expect(segment('In progress')).toBeChecked();
		expect(segment('Frontier')).not.toBeChecked();
	});

	it('checks neither segment when deselected', () => {
		render(WorkStateToggleGroup, { props: { value: undefined } });

		expect(segment('Frontier')).not.toBeChecked();
		expect(segment('In progress')).not.toBeChecked();
	});

	it('emits the selected work state', async () => {
		const onchange = vi.fn();
		render(WorkStateToggleGroup, { props: { value: undefined, onchange } });

		await fireEvent.click(segment('In progress'));

		expect(onchange).toHaveBeenCalledWith('inProgress');
	});

	it('emits undefined when the pressed Frontier segment is deselected', async () => {
		const onchange = vi.fn();
		render(WorkStateToggleGroup, { props: { value: 'frontier', onchange } });

		await fireEvent.click(segment('Frontier'));

		expect(onchange).toHaveBeenCalledWith(undefined);
	});

	it('emits undefined when the pressed In progress segment is deselected', async () => {
		const onchange = vi.fn();
		render(WorkStateToggleGroup, { props: { value: 'inProgress', onchange } });

		await fireEvent.click(segment('In progress'));

		expect(onchange).toHaveBeenCalledWith(undefined);
	});

	it('explains the frontier predicate when the segment is focused', async () => {
		render(WorkStateToggleGroup, { props: { value: undefined } });
		expect(screen.queryByText(FRONTIER_TOOLTIP)).not.toBeInTheDocument();

		await fireEvent.focus(segment('Frontier'));

		expect(await screen.findByText(FRONTIER_TOOLTIP)).toBeInTheDocument();
	});

	it('explains the In progress predicate when the segment is focused', async () => {
		render(WorkStateToggleGroup, { props: { value: undefined } });
		expect(screen.queryByText(IN_PROGRESS_TOOLTIP)).not.toBeInTheDocument();

		await fireEvent.focus(segment('In progress'));

		expect(await screen.findByText(IN_PROGRESS_TOOLTIP)).toBeInTheDocument();
	});

	it('hides the predicate again when focus leaves the segment', async () => {
		render(WorkStateToggleGroup, { props: { value: undefined } });
		const frontier = segment('Frontier');

		await fireEvent.focus(frontier);
		await screen.findByText(FRONTIER_TOOLTIP);

		await fireEvent.blur(frontier);
		await waitFor(() => {
			expect(screen.queryByText(FRONTIER_TOOLTIP)).not.toBeInTheDocument();
		});
	});
});