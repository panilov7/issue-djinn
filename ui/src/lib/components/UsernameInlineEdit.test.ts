import { fireEvent, render, screen } from '@testing-library/svelte';
import { beforeEach, describe, expect, it } from 'vitest';
import { username } from '$lib/stores/username.svelte';
import UsernameInlineEdit from './UsernameInlineEdit.svelte';

const KEY = 'issue-djinn.username';

describe('UsernameInlineEdit', () => {
	// The username store is a module singleton; reset it so tests are isolated.
	beforeEach(() => {
		localStorage.clear();
		username.clear();
	});

	it('prompts to set a username when none is set', () => {
		render(UsernameInlineEdit);
		expect(screen.getByRole('button', { name: /set username/i })).toBeInTheDocument();
	});

	it('displays the current username', () => {
		username.set('bob');
		render(UsernameInlineEdit);
		expect(screen.getByRole('button', { name: 'bob' })).toBeInTheDocument();
	});

	it('saves a typed username to localStorage', async () => {
		render(UsernameInlineEdit);
		await fireEvent.click(screen.getByRole('button', { name: /set username/i }));
		const input = screen.getByRole('textbox', { name: /username/i });
		await fireEvent.input(input, { target: { value: 'alice' } });
		await fireEvent.click(screen.getByRole('button', { name: /save username/i }));
		expect(localStorage.getItem(KEY)).toBe('alice');
		expect(screen.getByRole('button', { name: 'alice' })).toBeInTheDocument();
	});
});
