import { render, screen } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import IssueChildCounts from './IssueChildCounts.svelte';
import type { ChildCounts } from '$lib/types';

const counts: ChildCounts = { open: 5, closed: 2, total: 7 };

describe('IssueChildCounts', () => {
	it('renders the counts as one line: open, closed, total', () => {
		render(IssueChildCounts, { props: { counts } });
		expect(screen.getByText('5 open · 2 closed · 7 total')).toBeInTheDocument();
	});

	it('renders a zero-total line the same way', () => {
		render(IssueChildCounts, { props: { counts: { open: 0, closed: 0, total: 0 } } });
		expect(screen.getByText('0 open · 0 closed · 0 total')).toBeInTheDocument();
	});
});