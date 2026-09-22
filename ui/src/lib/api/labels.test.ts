import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { clearLabelsCache, getAllLabels } from './labels';

function jsonResponse(body: unknown, status = 200): Response {
	return {
		ok: status >= 200 && status < 300,
		status,
		json: () => Promise.resolve(body)
	} as Response;
}

describe('labels client', () => {
	beforeEach(() => clearLabelsCache());
	afterEach(() => vi.unstubAllGlobals());

	it('getAllLabels fetches /api/labels', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ labels: ['a', 'b'] }));
		vi.stubGlobal('fetch', fetchMock);
		const result = await getAllLabels();
		expect(fetchMock).toHaveBeenCalledWith('/api/labels', expect.objectContaining({}));
		expect(result.labels).toEqual(['a', 'b']);
	});

	it('caches the result for the session', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ labels: ['a', 'b'] }));
		vi.stubGlobal('fetch', fetchMock);
		await getAllLabels();
		await getAllLabels();
		expect(fetchMock).toHaveBeenCalledTimes(1);
	});
});
