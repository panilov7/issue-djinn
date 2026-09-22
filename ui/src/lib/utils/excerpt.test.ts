import { describe, it, expect } from 'vitest';
import { makeExcerpt, parseLabels } from './excerpt';

describe('makeExcerpt', () => {
	it('returns the full text when under the limit', () => {
		expect(makeExcerpt('short text', 160)).toBe('short text');
	});

	it('truncates and adds an ellipsis when over the limit', () => {
		const long = 'a'.repeat(200);
		const result = makeExcerpt(long, 50);
		expect(result.endsWith('…')).toBe(true);
		expect(result.length).toBe(51);
	});

	it('strips markdown characters', () => {
		expect(makeExcerpt('# Heading with `code`', 160)).toBe('Heading with code');
	});
});

describe('parseLabels', () => {
	it('splits comma-separated labels and trims', () => {
		expect(parseLabels('a, b , c')).toEqual(['a', 'b', 'c']);
	});

	it('deduplicates', () => {
		expect(parseLabels('a, b, a')).toEqual(['a', 'b']);
	});

	it('drops empty entries', () => {
		expect(parseLabels('a, , b,')).toEqual(['a', 'b']);
	});

	it('returns empty for empty string', () => {
		expect(parseLabels('')).toEqual([]);
	});
});
