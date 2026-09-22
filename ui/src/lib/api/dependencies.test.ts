import { afterEach, describe, expect, it, vi } from 'vitest';
import { addDependency, removeDependency } from './dependencies';

function jsonResponse(body: unknown, status = 200): Response {
	return {
		ok: status >= 200 && status < 300,
		status,
		json: () => Promise.resolve(body)
	} as Response;
}

describe('dependencies client', () => {
	afterEach(() => vi.unstubAllGlobals());

	it('addDependency POSTs the snake_case dependency_id', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse(null, 204));
		vi.stubGlobal('fetch', fetchMock);
		await addDependency(5, 9);
		const [url, init] = fetchMock.mock.calls[0];
		expect(url).toBe('/api/issues/5/dependencies');
		expect((init as RequestInit).method).toBe('POST');
		expect(JSON.parse((init as RequestInit).body as string)).toEqual({ dependency_id: 9 });
	});

	it('removeDependency DELETEs the dependency path', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse(null, 204));
		vi.stubGlobal('fetch', fetchMock);
		await removeDependency(5, 9);
		expect(fetchMock).toHaveBeenCalledWith('/api/issues/5/dependencies/9', expect.objectContaining({ method: 'DELETE' }));
	});
});
