import { render, screen } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import ThemeToggle from './ThemeToggle.svelte';

describe('ThemeToggle', () => {
	it('renders a theme toggle button', () => {
		render(ThemeToggle);
		expect(screen.getByRole('button', { name: /toggle theme/i })).toBeInTheDocument();
	});
});
