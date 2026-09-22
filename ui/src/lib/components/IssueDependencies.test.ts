import { fireEvent, render, screen } from '@testing-library/svelte';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import IssueDependencies from './IssueDependencies.svelte';
import { listIssues } from '$lib/api/issues';
import type { IssueDependency, IssueListItem } from '$lib/types';

vi.mock('$lib/api/issues', () => ({
	listIssues: vi.fn()
}));

const dependencies: IssueDependency[] = [
	{ id: 2, title: 'Bootstrap the tracker', status: 'open' },
	{ id: 3, title: 'Add a combobox', status: 'closed' }
];

const dependents: IssueDependency[] = [{ id: 9, title: 'Write the smoke script', status: 'open' }];

/** An issue as the picker's list shape returns it. */
function issue(id: number, title: string, status: 'open' | 'closed' = 'open'): IssueListItem {
	return {
		id,
		title,
		status,
		assignee: null,
		labels: [],
		parentId: null,
		updatedAt: '',
		childCounts: { open: 0, closed: 0, total: 0 },
		matchesFilter: true,
		matchingChildCount: 0,
		children: []
	};
}

function baseProps(overrides: Record<string, unknown> = {}) {
	return {
		issueId: 1,
		dependencies: [],
		dependents: [],
		onAddDependency: vi.fn(),
		onRemoveDependency: vi.fn(),
		...overrides
	};
}

describe('IssueDependencies', () => {
	afterEach(() => vi.restoreAllMocks());

	beforeEach(() => vi.clearAllMocks());

	it('renders Depends on and Dependents panels with links', () => {
		render(IssueDependencies, {
			props: baseProps({ dependencies, dependents })
		});
		expect(screen.getByText('Depends on')).toBeInTheDocument();
		expect(screen.getByText('Dependents')).toBeInTheDocument();
		expect(screen.getByRole('link', { name: /bootstrap the tracker/i })).toHaveAttribute(
			'href',
			'/issues/2'
		);
	});

	it('shows the empty state for both panels', () => {
		render(IssueDependencies, { props: baseProps() });
		expect(screen.getAllByText('No dependencies.')).toHaveLength(2);
	});

	it('offers + Add dependency in the Depends on panel', () => {
		render(IssueDependencies, { props: baseProps() });
		expect(screen.getByRole('button', { name: /add dependency/i })).toBeInTheDocument();
	});
});

describe('IssueDependencies picker', () => {
	afterEach(() => vi.restoreAllMocks());

	beforeEach(() => vi.clearAllMocks());

	it('excludes the issue itself and its existing dependencies', async () => {
		vi.mocked(listIssues).mockResolvedValue({
			issues: [
				issue(1, 'This issue'),
				issue(2, 'Bootstrap the tracker'),
				issue(3, 'Add a combobox'),
				issue(4, 'A brand new dependency')
			]
		});

		render(IssueDependencies, { props: baseProps({ dependencies }) });
		await fireEvent.click(screen.getByRole('button', { name: /add dependency/i }));

		await vi.waitFor(() =>
			expect(screen.getByRole('option', { name: /A brand new dependency/ })).toBeInTheDocument()
		);
		expect(screen.queryByRole('option', { name: /this issue/ })).not.toBeInTheDocument();
		expect(screen.queryByRole('option', { name: /bootstrap the tracker/ })).not.toBeInTheDocument();
		expect(screen.queryByRole('option', { name: /add a combobox/ })).not.toBeInTheDocument();
	});

	it('offers closed issues alongside open ones', async () => {
		vi.mocked(listIssues).mockResolvedValue({
			issues: [issue(4, 'Already closed', 'closed'), issue(5, 'Still open')]
		});

		render(IssueDependencies, { props: baseProps() });
		await fireEvent.click(screen.getByRole('button', { name: /add dependency/i }));

		await vi.waitFor(() => {
			// A closed dependency does not block progress, so it is a legal one.
			expect(screen.getByRole('option', { name: /Already closed/ })).toBeInTheDocument();
			expect(screen.getByRole('option', { name: /Still open/ })).toBeInTheDocument();
		});
	});

	it('adds the selected issue as a dependency', async () => {
		vi.mocked(listIssues).mockResolvedValue({ issues: [issue(4, 'A brand new dependency')] });

		const props = baseProps();
		render(IssueDependencies, { props });
		await fireEvent.click(screen.getByRole('button', { name: /add dependency/i }));
		await vi.waitFor(() => screen.getByRole('option', { name: /A brand new dependency/ }));

		await fireEvent.click(screen.getByRole('option', { name: /A brand new dependency/ }));

		expect(props.onAddDependency).toHaveBeenCalledWith(4);
	});

	it('adds a closed issue as a dependency', async () => {
		vi.mocked(listIssues).mockResolvedValue({ issues: [issue(4, 'Already closed', 'closed')] });

		const props = baseProps();
		render(IssueDependencies, { props });
		await fireEvent.click(screen.getByRole('button', { name: /add dependency/i }));
		await vi.waitFor(() => screen.getByRole('option', { name: /Already closed/ }));

		await fireEvent.click(screen.getByRole('option', { name: /Already closed/ }));

		expect(props.onAddDependency).toHaveBeenCalledWith(4);
	});

	it('offers the All/Open/Closed filter in the popover', async () => {
		vi.mocked(listIssues).mockResolvedValue({ issues: [issue(4, 'A brand new dependency')] });

		render(IssueDependencies, { props: baseProps() });
		await fireEvent.click(screen.getByRole('button', { name: /add dependency/i }));

		await vi.waitFor(() => expect(screen.getByRole('radio', { name: 'All' })).toBeChecked());
	});
});

describe('IssueDependencies remove', () => {
	it('removes a dependency from its × button', async () => {
		const props = baseProps({ dependencies });
		render(IssueDependencies, { props });

		await fireEvent.click(
			screen.getByRole('button', { name: 'Remove dependency Bootstrap the tracker' })
		);

		expect(props.onRemoveDependency).toHaveBeenCalledWith(2);
	});

	it('offers one remove button per dependency', () => {
		render(IssueDependencies, { props: baseProps({ dependencies }) });
		expect(screen.getAllByRole('button', { name: /^Remove dependency/ })).toHaveLength(
			dependencies.length
		);
	});

	it('keeps the Dependents panel read-only', () => {
		render(IssueDependencies, { props: baseProps({ dependencies, dependents }) });

		expect(
			screen.queryByRole('button', { name: 'Remove dependency Write the smoke script' })
		).not.toBeInTheDocument();
	});
});

describe('IssueDependencies cycle error', () => {
	it('surfaces the API error message verbatim in an alert banner', () => {
		const message = 'Cannot add dependency: would create a cycle between issue 1 and issue 3';
		render(IssueDependencies, { props: baseProps({ cycleError: message }) });

		expect(screen.getByRole('alert')).toHaveTextContent(message);
	});

	it('shows no alert banner without a cycle error', () => {
		render(IssueDependencies, { props: baseProps({ dependencies }) });

		expect(screen.queryByRole('alert')).not.toBeInTheDocument();
	});
});
