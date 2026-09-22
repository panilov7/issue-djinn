import { render, screen } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import IssueChildRows from './IssueChildRows.svelte';
import type { IssueListItem, IssueSummary } from '$lib/types';

const root: IssueListItem = {
	id: 3,
	title: 'Parent issue',
	status: 'open',
	assignee: null,
	labels: [],
	parentId: null,
	updatedAt: new Date().toISOString(),
	childCounts: { open: 4, closed: 0, total: 4 },
	matchesFilter: true,
	matchingChildCount: 4,
	children: []
};

/** An embedded child: the light row shape the list hands over, titled after its id. */
function child(id: number): IssueSummary {
	return {
		id,
		title: `Child ${id}`,
		status: 'open',
		assignee: null,
		labels: [],
		parentId: root.id,
		updatedAt: new Date().toISOString()
	};
}

describe('IssueChildRows', () => {
	it('renders the embedded children as indented rows linking to them', () => {
		render(IssueChildRows, { props: { root: { ...root, children: [child(11), child(12)] } } });

		for (const id of [11, 12]) {
			const row = screen.getByRole('link', { name: new RegExp(`#${id} Child ${id}`) });
			expect(row).toHaveAttribute('href', `/issues/${id}`);
			expect(row).toHaveClass('pl-14');
		}
	});

	it('renders an overflow row linking to the parent when the cap hid children', () => {
		render(
			IssueChildRows,
			{ props: { root: { ...root, matchingChildCount: 4, children: [child(11)] } } }
		);

		const overflow = screen.getByRole('link', { name: /and 3 more/i });
		expect(overflow).toHaveAttribute('href', '/issues/3');
	});

	it('keeps the bare detail URL at the default child order', () => {
		// With the default (no child_direction sent),
		// today's behavior is unchanged.
		render(
			IssueChildRows,
			{ props: { root: { ...root, matchingChildCount: 4, children: [child(11)] }, childDirection: 'asc' } }
		);

		expect(screen.getByRole('link', { name: /and 3 more/i })).toHaveAttribute('href', '/issues/3');
	});

	it('carries child_direction=desc on the overflow link when the listing runs newest-first', () => {
		// The cap keeps the newest 10 in a flipped listing, so the link must land
		// the detail page at the same end of the child list the preview showed.
		render(
			IssueChildRows,
			{ props: { root: { ...root, matchingChildCount: 4, children: [child(11)] }, childDirection: 'desc' } }
		);

		expect(screen.getByRole('link', { name: /and 3 more/i })).toHaveAttribute(
			'href',
			'/issues/3?child_direction=desc'
		);
	});

	it('renders no overflow row when every matching child is embedded', () => {
		render(IssueChildRows, { props: { root: { ...root, matchingChildCount: 1, children: [child(11)] } } });

		expect(screen.queryByRole('link', { name: /more/i })).not.toBeInTheDocument();
		expect(screen.getByRole('link', { name: /#11 Child 11/i })).toBeInTheDocument();
	});
});
