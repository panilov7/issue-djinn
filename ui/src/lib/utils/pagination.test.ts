import { describe, it, expect } from 'vitest';
import { paginationRange } from './pagination';

describe('paginationRange', () => {
	it('returns full range when totalPages is small', () => {
		expect(paginationRange(1, 5)).toEqual([1, 2, 3, 4, 5]);
	});

	it('returns full range for exactly 7 pages', () => {
		expect(paginationRange(1, 7)).toEqual([1, 2, 3, 4, 5, 6, 7]);
	});

	it('shows ellipsis near the start when current is low', () => {
		expect(paginationRange(2, 10)).toEqual([1, 2, 3, 4, 5, 'ellipsis', 10]);
	});

	it('shows ellipsis near the end when current is high', () => {
		expect(paginationRange(9, 10)).toEqual([1, 'ellipsis', 6, 7, 8, 9, 10]);
	});

	it('shows two ellipses in the middle', () => {
		expect(paginationRange(5, 10)).toEqual([1, 'ellipsis', 4, 5, 6, 'ellipsis', 10]);
	});
});
