import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, request } from './client';

function jsonResponse(body: unknown, status = 200): Response {
	return {
		ok: status >= 200 && status < 300,
		status,
		json: () => Promise.resolve(body)
	} as Response;
}

describe('request', () => {
	afterEach(() => vi.unstubAllGlobals());

	it('parses a JSON body', async () => {
		vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ issues: [] })));
		const data = await request<{ issues: unknown[] }>('/api/issues');
		expect(data.issues).toEqual([]);
	});

	it('sends a JSON content-type header', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({}));
		vi.stubGlobal('fetch', fetchMock);
		await request('/api/issues', { method: 'POST', body: '{}' });
		expect(fetchMock).toHaveBeenCalledWith('/api/issues', {
			method: 'POST',
			body: '{}',
			headers: { 'content-type': 'application/json' }
		});
	});

	it('returns undefined for a 204 response', async () => {
		vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(null, 204)));
		await expect(request('/api/x', { method: 'DELETE' })).resolves.toBeUndefined();
	});

	it('throws ApiError with the error-envelope code and message', async () => {
		vi.stubGlobal(
			'fetch',
			vi.fn().mockResolvedValue(jsonResponse({ error: { code: 'ISSUE_NOT_FOUND', message: 'nope' } }, 404))
		);
		const error = await request('/api/issues/9').catch((e: unknown) => e);
		expect(error).toBeInstanceOf(ApiError);
		expect((error as ApiError).status).toBe(404);
		expect((error as ApiError).code).toBe('ISSUE_NOT_FOUND');
		expect((error as ApiError).message).toBe('nope');
	});

	it('falls back to HTTP status code when the body is not a JSON envelope', async () => {
		vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse('oops', 500)));
		const error = await request('/api/x').catch((e: unknown) => e);
		expect((error as ApiError).code).toBe('HTTP_500');
	});
});
