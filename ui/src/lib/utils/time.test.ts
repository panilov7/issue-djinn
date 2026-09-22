import { describe, expect, it, vi } from 'vitest';
import { formatTimestamp, relativeTime } from './time';

describe('relativeTime', () => {
	beforeEach(() => {
		vi.useFakeTimers();
		vi.setSystemTime(new Date('2024-01-01T12:00:00Z'));
	});

	afterEach(() => {
		vi.useRealTimers();
	});

	it('returns "just now" within the first minute', () => {
		expect(relativeTime('2024-01-01T11:59:30Z')).toBe('just now');
	});

	it('returns minutes ago', () => {
		expect(relativeTime('2024-01-01T11:58:00Z')).toBe('2 minutes ago');
	});

	it('returns singular minute', () => {
		expect(relativeTime('2024-01-01T11:59:00Z')).toBe('1 minute ago');
	});

	it('returns hours ago', () => {
		expect(relativeTime('2024-01-01T10:00:00Z')).toBe('2 hours ago');
	});

	it('returns days ago', () => {
		expect(relativeTime('2023-12-31T12:00:00Z')).toBe('1 day ago');
	});

	it('returns months ago', () => {
		expect(relativeTime('2023-11-01T12:00:00Z')).toBe('2 months ago');
	});

	it('returns years ago', () => {
		expect(relativeTime('2022-01-01T12:00:00Z')).toBe('2 years ago');
	});
});

describe('formatTimestamp', () => {
	it('formats a date for display', () => {
		const out = formatTimestamp('2024-01-01T10:30:00Z');
		expect(out).toContain('2024');
	});
});
