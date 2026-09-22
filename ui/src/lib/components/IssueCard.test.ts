import { render, screen } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import IssueCard from './IssueCard.svelte';
import type { IssueListItem } from '$lib/types';

const issue: IssueListItem = {
	id: 7,
	title: 'Bootstrap the issue-djinn tracker',
	status: 'open',
	assignee: null,
	labels: ['needs-triage'],
	parentId: null,
	updatedAt: new Date().toISOString(),
	childCounts: { open: 0, closed: 0, total: 0 },
	matchesFilter: true,
	matchingChildCount: 0,
	children: []
};

const childlessIssue: IssueListItem = { ...issue, id: 8, title: 'Childless issue' };
const rootWithChildren: IssueListItem = {
	...issue,
	id: 9,
	title: 'Root with children',
	childCounts: { open: 5, closed: 2, total: 7 }
};
const closedRoot: IssueListItem = {
	...issue,
	id: 10,
	title: 'Closed root with an open child',
	status: 'closed',
	matchesFilter: false
};

describe('IssueCard', () => {
	it('renders the issue id, title and status', () => {
		render(IssueCard, { props: { issue } });
		expect(screen.getByText('Bootstrap the issue-djinn tracker')).toBeInTheDocument();
		expect(screen.getByText('#7')).toBeInTheDocument();
		expect(screen.getByText('open')).toBeInTheDocument();
		expect(screen.getByText('needs-triage')).toBeInTheDocument();
	});

	it('is a link to the issue detail page', () => {
		render(IssueCard, { props: { issue } });
		const link = screen.getByRole('link', { name: /bootstrap the issue-djinn tracker/i });
		expect(link).toHaveAttribute('href', '/issues/7');
	});

	it('does not render an avatar when unassigned', () => {
		render(IssueCard, { props: { issue } });
		expect(screen.queryByRole('img')).not.toBeInTheDocument();
	});

	it('renders a child counts line under the title of a root that has children', () => {
		render(IssueCard, { props: { issue: rootWithChildren, childCounts: rootWithChildren.childCounts } });
		expect(screen.getByText('5 open · 2 closed · 7 total')).toBeInTheDocument();
	});

	it('renders no child counts line for a childless issue', () => {
		render(IssueCard, { props: { issue: childlessIssue } });
		expect(screen.queryByText(/open · .* closed/)).not.toBeInTheDocument();
	});

	it('indents a nested child row under its root', () => {
		render(IssueCard, { props: { issue, indent: true } });
		expect(screen.getByRole('link')).toHaveClass('pl-14');
	});

	it('does not indent a root row', () => {
		render(IssueCard, { props: { issue } });
		expect(screen.getByRole('link')).not.toHaveClass('pl-14');
	});

	it('dims a container root and explains why on hover', () => {
		render(IssueCard, { props: { issue: closedRoot, dimmed: true } });
		const link = screen.getByRole('link');
		expect(link).toHaveClass('opacity-60');
		// The explanation rides on the row and on the status badge, whose "closed"
		// is what contradicts the filters.
		expect(link).toHaveAttribute('title', expect.stringContaining('child issue'));
		expect(screen.getByText('closed')).toHaveAttribute('title', expect.stringContaining('child issue'));
	});

	it('does not dim a row that matches the filters', () => {
		render(IssueCard, { props: { issue } });
		const link = screen.getByRole('link');
		expect(link).not.toHaveClass('opacity-60');
		expect(link).not.toHaveAttribute('title');
	});
});
