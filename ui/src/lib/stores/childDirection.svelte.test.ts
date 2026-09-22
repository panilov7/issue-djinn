import { describe, expect, it } from 'vitest';
import { parseChildDirection } from './childDirection.svelte';

describe('parseChildDirection', () => {
	it('accepts the two whitelisted directions', () => {
		expect(parseChildDirection('asc')).toBe('asc');
		expect(parseChildDirection('desc')).toBe('desc');
	});

	it.each([
		['a non-direction string', 'oldest'],
		['a wrong-typed blob', 7],
		['null', null]
	])('rejects %s so the store keeps its default', (_, raw) => {
		expect(parseChildDirection(raw)).toBeUndefined();
	});
});