import { afterEach, describe, expect, it, vi } from 'vitest';
import { addComment, listComments } from './comments';

function jsonResponse(body: unknown, status = 200): Response {
	return {
		ok: status >= 200 && status < 300,
		status,
		json: () => Promise.resolve(body)
	} as Response;
}

describe('comments client', () => {
	afterEach(() => vi.unstubAllGlobals());

	it('listComments requests the issue comments path', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ comments: [] }));
		vi.stubGlobal('fetch', fetchMock);
		await listComments(7);
		expect(fetchMock).toHaveBeenCalledWith('/api/issues/7/comments', expect.objectContaining({}));
	});

	it('addComment POSTs author and body', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: 1 }));
		vi.stubGlobal('fetch', fetchMock);
		await addComment(7, 'bob', 'hello');
		const [url, init] = fetchMock.mock.calls[0];
		expect(url).toBe('/api/issues/7/comments');
		expect((init as RequestInit).method).toBe('POST');
		expect(JSON.parse((init as RequestInit).body as string)).toEqual({ author: 'bob', body: 'hello' });
	});
});
