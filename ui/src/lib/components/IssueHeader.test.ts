import { fireEvent, render, screen } from '@testing-library/svelte';
import { describe, expect, it, vi } from 'vitest';
import IssueHeader from './IssueHeader.svelte';
import type { IssueDetail } from '$lib/types';

const base: IssueDetail = {
	id: 1,
	title: 'Bootstrap the tracker',
	description: '',
	status: 'open',
	assignee: null,
	createdAt: new Date().toISOString(),
	updatedAt: new Date().toISOString(),
	parentId: null,
	labels: ['integration'],
	commentCount: 0,
	dependencies: [],
	dependents: [],
	children: []
};

function makeProps(overrides: Partial<IssueDetail> = {}) {
	return {
		issue: { ...base, ...overrides },
		onEdit: vi.fn(),
		onClose: vi.fn(),
		onReopen: vi.fn(),
		onClaim: vi.fn(),
		onUnclaim: vi.fn()
	};
}

describe('IssueHeader', () => {
	it('renders title, status, assignee and labels', () => {
		render(IssueHeader, { props: makeProps() });
		expect(screen.getByText('Bootstrap the tracker')).toBeInTheDocument();
		expect(screen.getByText('integration')).toBeInTheDocument();
		expect(screen.getByText(/assignee:/)).toBeInTheDocument();
	});

	it('shows Claim and Close for an open unassigned issue, never Reopen', () => {
		render(IssueHeader, { props: makeProps() });
		expect(screen.getByRole('button', { name: /claim/i })).toBeInTheDocument();
		expect(screen.getByRole('button', { name: /close issue/i })).toBeInTheDocument();
		expect(screen.queryByRole('button', { name: /reopen/i })).not.toBeInTheDocument();
	});

	it('shows Reopen and Unclaim for a closed assigned issue', () => {
		render(IssueHeader, { props: makeProps({ status: 'closed', assignee: 'bob' }) });
		expect(screen.getByRole('button', { name: /reopen/i })).toBeInTheDocument();
		expect(screen.getByRole('button', { name: /unclaim/i })).toBeInTheDocument();
		expect(screen.queryByRole('button', { name: /close issue/i })).not.toBeInTheDocument();
	});

	it('calls onEdit when Edit is clicked', async () => {
		const p = makeProps();
		render(IssueHeader, { props: p });
		await fireEvent.click(screen.getByRole('button', { name: /edit/i }));
		expect(p.onEdit).toHaveBeenCalledTimes(1);
	});

	it('calls onClaim when Claim is clicked', async () => {
		const p = makeProps();
		render(IssueHeader, { props: p });
		await fireEvent.click(screen.getByRole('button', { name: /claim/i }));
		expect(p.onClaim).toHaveBeenCalledTimes(1);
	});

	it('leads the meta row with the parent link', () => {
		render(IssueHeader, { props: makeProps({ parentId: 3 }) });

		const meta = screen.getByText(/assignee:/).closest('[data-slot="issue-meta"]');
		expect(meta?.firstElementChild).toHaveTextContent('parent:');
		expect(meta?.firstElementChild).toContainElement(screen.getByRole('link', { name: '#3' }));
		expect(screen.getByRole('link', { name: '#3' })).toHaveAttribute('href', '/issues/3');
	});

	it('renders a top-level issue as unparented', () => {
		render(IssueHeader, { props: makeProps({ parentId: null }) });
		expect(screen.getByText(/(top-level)/)).toBeInTheDocument();
		expect(screen.queryByRole('link')).not.toBeInTheDocument();
	});
});
