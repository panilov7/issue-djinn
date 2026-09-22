import DOMPurify from 'dompurify';
import { marked } from 'marked';

marked.setOptions({
	gfm: true,
	breaks: false
});

// Markdown is rendered client-side only; the server never renders markdown.
export function renderMarkdown(src: string): string {
	const raw = marked.parse(src, { async: false }) as string;
	return DOMPurify.sanitize(raw);
}
