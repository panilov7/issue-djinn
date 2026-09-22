import { afterEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/svelte';
import IssueComments from './IssueComments.svelte';
import { username } from '$lib/stores/username.svelte';

// Offset from now so `relativeTime` says "just now" even if the suite runs
// slowly across a minute boundary.
const recent = new Date(Date.now() - 30_000).toISOString();
const c1 = { id: 1, author: 'alice', body: 'First comment', createdAt: recent };
const c2 = { id: 2, author: 'bob', body: 'Second comment', createdAt: recent };

describe('IssueComments', () => {
	afterEach(() => username.clear());

	it('renders the comment thread', () => {
		render(IssueComments, { props: { comments: [c1, c2], onAdd: vi.fn() } });
		expect(screen.getByText('First comment')).toBeInTheDocument();
		expect(screen.getByText('Second comment')).toBeInTheDocument();
		expect(screen.getByText('alice')).toBeInTheDocument();
	});

	it('shows a markdown editor with toolbar when adding a comment', async () => {
		render(IssueComments, { props: { comments: [], onAdd: vi.fn() } });
		await fireEvent.click(screen.getByRole('button', { name: /add comment/i }));
		expect(screen.getByRole('toolbar', { name: 'Formatting' })).toBeInTheDocument();
	});

	it('adds a comment with the supplied author and body', async () => {
		username.set('bob');
		const onAdd = vi.fn().mockResolvedValue(undefined);
		render(IssueComments, { props: { comments: [], onAdd } });
		await fireEvent.click(screen.getByRole('button', { name: /add comment/i }));
		await fireEvent.input(screen.getByLabelText(/author/i), { target: { value: 'bob' } });
		await fireEvent.input(screen.getByLabelText(/comment/i), { target: { value: 'Hello' } });
		await fireEvent.click(screen.getByRole('button', { name: 'Add comment' }));
		await waitFor(() => expect(onAdd).toHaveBeenCalledWith('bob', 'Hello'));
	});

	it('shows an error alert on failure and retains the draft', async () => {
		username.set('bob');
		const onAdd = vi.fn().mockRejectedValue(new Error('boom'));
		render(IssueComments, { props: { comments: [], onAdd } });
		await fireEvent.click(screen.getByRole('button', { name: /add comment/i }));
		await fireEvent.input(screen.getByLabelText(/author/i), { target: { value: 'bob' } });
		await fireEvent.input(screen.getByLabelText(/comment/i), { target: { value: 'Hello' } });
		await fireEvent.click(screen.getByRole('button', { name: 'Add comment' }));
		expect(await screen.findByRole('alert')).toBeInTheDocument();
		expect(screen.getByLabelText(/comment/i)).toHaveValue('Hello');
	});
});

describe('IssueComments section card', () => {
	it('renders the section as a single card with a counted header', () => {
		render(IssueComments, { props: { comments: [c1, c2], onAdd: vi.fn() } });
		// One card — the section itself; comments are never their own cards.
		expect(document.querySelectorAll('[data-slot="card"]')).toHaveLength(1);
		expect(screen.getByText(/^Comments/)).toHaveTextContent('2');
	});

	it('counts zero comments in the header and keeps the empty state inside the card', () => {
		render(IssueComments, { props: { comments: [], onAdd: vi.fn() } });
		expect(screen.getByText(/^Comments/)).toHaveTextContent('0');
		expect(screen.getByText('No comments yet.')).toBeInTheDocument();
	});

	it('separates adjacent comments with hairline dividers', () => {
		render(IssueComments, { props: { comments: [c1, c2], onAdd: vi.fn() } });
		const list = document.querySelector('[data-slot="card"] ul');
		expect(list).toBeInTheDocument();
		expect(list).toHaveClass('divide-y', 'divide-border');
		expect(list!.children).toHaveLength(2);
	});

	it('shows avatar, author and relative timestamp in a meta row above each body', () => {
		render(IssueComments, { props: { comments: [c1, c2], onAdd: vi.fn() } });
		const items = document.querySelectorAll('[data-slot="card"] li');
		expect(items).toHaveLength(2);
		for (const item of items) {
			const avatar = item.querySelector('[data-slot="avatar"]');
			const body = item.querySelector('[data-slot="markdown-view"]');
			expect(avatar).toBeInTheDocument();
			expect(body).toBeInTheDocument();
			// The avatar's meta row comes before the comment body.
			expect(avatar!.compareDocumentPosition(body!) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
		}
		const avatars = document.querySelectorAll('[data-slot="card"] [data-slot="avatar"]');
		expect(avatars[0]).toHaveAttribute('title', 'alice');
		expect(avatars[1]).toHaveAttribute('title', 'bob');
		expect(screen.getByText('alice')).toBeInTheDocument();
		expect(screen.getAllByText(/just now/)).toHaveLength(2);
	});

	it('renders the composer on a muted background below the feed', () => {
		render(IssueComments, { props: { comments: [c1], onAdd: vi.fn() } });
		const composer = screen
			.getByRole('button', { name: /add comment/i })
			.closest('[data-slot="card-footer"]');
		expect(composer).toBeInTheDocument();
		expect(composer).toHaveClass('bg-muted/50', 'border-border');
	});
});
