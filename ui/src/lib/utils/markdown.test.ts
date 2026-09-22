import { describe, expect, it } from 'vitest';
import { renderMarkdown } from './markdown';

describe('renderMarkdown', () => {
	it('renders markdown to HTML', () => {
		const html = renderMarkdown('# Title\n\n**bold**');
		expect(html).toContain('<h1');
		expect(html).toContain('<strong>');
	});

	it('supports GFM tables', () => {
		const html = renderMarkdown('| a | b |\n|---|---|\n| 1 | 2 |');
		expect(html).toContain('<table>');
	});

	it('strips script tags via DOMPurify', () => {
		const html = renderMarkdown('<script>alert(1)</script>');
		expect(html).not.toContain('<script');
	});

	it('strips event-handler attributes via DOMPurify', () => {
		const html = renderMarkdown('<img src="x" onerror="alert(1)" />');
		expect(html).not.toContain('onerror');
	});
});
