import { render, screen } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import IssueDescription from './IssueDescription.svelte';

describe('IssueDescription', () => {
	it('renders the description as sanitized markdown', () => {
		render(IssueDescription, { props: { description: '# Title' } });
		expect(screen.getByTestId('markdown-view')).toContainHTML('<h1');
	});

	it('renders the description inside a card with a Description header', () => {
		render(IssueDescription, { props: { description: '# Title' } });

		const card = document.querySelector('[data-slot="card"]');
		expect(card).toContainElement(screen.getByText('Description'));
		expect(card).toContainElement(screen.getByTestId('markdown-view'));
	});

	it('has no action slot or Edit button of its own — editing stays in the issue header', () => {
		render(IssueDescription, { props: { description: 'Body' } });

		expect(document.querySelector('[data-slot="card-action"]')).toBeNull();
		expect(screen.queryByRole('button', { name: /edit/i })).not.toBeInTheDocument();
	});
});
