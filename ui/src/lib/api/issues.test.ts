import { afterEach, describe, expect, it, vi } from 'vitest';
import { closeIssue, createIssue, getIssue, listIssues, reopenIssue, unassignIssue, updateIssue } from './issues';

function jsonResponse(body: unknown, status = 200): Response {
	return {
		ok: status >= 200 && status < 300,
		status,
		json: () => Promise.resolve(body)
	} as Response;
}

describe('issues client', () => {
	afterEach(() => vi.unstubAllGlobals());

	it('listIssues builds a query string from filters', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: [] }));
		vi.stubGlobal('fetch', fetchMock);
		await listIssues({ status: 'open', labels: ['a', 'b'], search: 'hi', hasAssignee: false });
		const url = fetchMock.mock.calls[0][0] as string;
		expect(url).toContain('status=open');
		expect(url).toContain('label=a');
		expect(url).toContain('label=b');
		expect(url).toContain('search=hi');
		expect(url).toContain('has_assignee=false');
	});

	it('listIssues serializes the flat opt-out and omits it by default', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: [] }));
		vi.stubGlobal('fetch', fetchMock);
		await listIssues({ status: 'open', flat: true });
		expect(fetchMock.mock.calls[0][0]).toContain('flat=true');

		await listIssues({ status: 'open' });
		expect(fetchMock.mock.calls[1][0]).toBe('/api/issues?status=open');
	});

	it('listIssues omits the status param for the "all" filter', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: [] }));
		vi.stubGlobal('fetch', fetchMock);
		await listIssues({ status: 'all' });
		expect(fetchMock.mock.calls[0][0]).toBe('/api/issues');
	});

	it('listIssues serializes the closed status filter', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: [] }));
		vi.stubGlobal('fetch', fetchMock);
		await listIssues({ status: 'closed' });
		expect(fetchMock.mock.calls[0][0]).toContain('status=closed');
	});

	it('listIssues without filters hits the base path', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: [] }));
		vi.stubGlobal('fetch', fetchMock);
		await listIssues();
		expect(fetchMock).toHaveBeenCalledWith('/api/issues', expect.objectContaining({}));
	});

	it('listIssues serializes the parent sort and child direction as snake_case params', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: [] }));
		vi.stubGlobal('fetch', fetchMock);
		await listIssues({ sort: 'createdAt', direction: 'desc', childDirection: 'asc' });
		const url = fetchMock.mock.calls[0][0] as string;
		expect(url).toContain('sort=createdAt');
		expect(url).toContain('direction=desc');
		expect(url).toContain('child_direction=asc');
	});

	it('listIssues omits the sort params when the caller sends none', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ issues: [] }));
		vi.stubGlobal('fetch', fetchMock);
		await listIssues({ status: 'open' });
		const url = fetchMock.mock.calls[0][0] as string;
		expect(url).toBe('/api/issues?status=open');
	});

	it('getIssue requests the detail path', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: 42 }));
		vi.stubGlobal('fetch', fetchMock);
		await getIssue(42);
		expect(fetchMock).toHaveBeenCalledWith('/api/issues/42', expect.objectContaining({}));
	});

	it('getIssue carries the child direction as child_direction', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: 42 }));
		vi.stubGlobal('fetch', fetchMock);
		await getIssue(42, 'desc');
		expect(fetchMock).toHaveBeenCalledWith(
			'/api/issues/42?child_direction=desc',
			expect.objectContaining({})
		);
	});

	it('createIssue POSTs title and description', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: 1 }));
		vi.stubGlobal('fetch', fetchMock);
		await createIssue({ title: 'T', description: 'D' });
		const [url, init] = fetchMock.mock.calls[0];
		expect(url).toBe('/api/issues');
		expect((init as RequestInit).method).toBe('POST');
		expect(JSON.parse((init as RequestInit).body as string)).toEqual({ title: 'T', description: 'D' });
	});

	it('createIssue sends the optional parent and labels when given', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: 1 }));
		vi.stubGlobal('fetch', fetchMock);
		await createIssue({ title: 'T', parentId: 7, labels: ['bug', 'ready-for-agent'] });
		const [url, init] = fetchMock.mock.calls[0];
		expect(url).toBe('/api/issues');
		expect((init as RequestInit).method).toBe('POST');
		expect(JSON.parse((init as RequestInit).body as string)).toEqual({
			title: 'T',
			parentId: 7,
			labels: ['bug', 'ready-for-agent']
		});
	});

	it('createIssue sends an explicit empty parent and no labels', async () => {
		// The body the dialog sends when nothing is picked: nulled out, not dropped.
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: 1 }));
		vi.stubGlobal('fetch', fetchMock);
		await createIssue({ title: 'T', description: 'D', parentId: null, labels: [] });
		const [url, init] = fetchMock.mock.calls[0];
		expect(url).toBe('/api/issues');
		expect(JSON.parse((init as RequestInit).body as string)).toEqual({
			title: 'T',
			description: 'D',
			parentId: null,
			labels: []
		});
	});

	it('updateIssue PATCHes only provided fields', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: 1 }));
		vi.stubGlobal('fetch', fetchMock);
		await updateIssue(1, { labels: ['a'] });
		const [url, init] = fetchMock.mock.calls[0];
		expect(url).toBe('/api/issues/1');
		expect((init as RequestInit).method).toBe('PATCH');
		expect(JSON.parse((init as RequestInit).body as string)).toEqual({ labels: ['a'] });
	});

	it('updateIssue sends an emptied label array so every label can be removed', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: 1 }));
		vi.stubGlobal('fetch', fetchMock);
		await updateIssue(1, { labels: [] });
		expect(JSON.parse((fetchMock.mock.calls[0][1] as RequestInit).body as string)).toEqual({
			labels: []
		});
	});

	it('closeIssue POSTs comment and author', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: 1 }));
		vi.stubGlobal('fetch', fetchMock);
		await closeIssue(1, 'resolved', 'bob');
		const [, init] = fetchMock.mock.calls[0];
		expect((init as RequestInit).method).toBe('POST');
		expect(JSON.parse((init as RequestInit).body as string)).toEqual({ comment: 'resolved', author: 'bob' });
	});

	it('reopenIssue POSTs comment and author', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: 1 }));
		vi.stubGlobal('fetch', fetchMock);
		await reopenIssue(1, 'reopened', 'bob');
		const [, init] = fetchMock.mock.calls[0];
		expect((init as RequestInit).method).toBe('POST');
		expect(JSON.parse((init as RequestInit).body as string)).toEqual({ comment: 'reopened', author: 'bob' });
	});

	it('unassignIssue POSTs the unassign endpoint', async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: 1 }));
		vi.stubGlobal('fetch', fetchMock);
		await unassignIssue(1);
		expect(fetchMock).toHaveBeenCalledWith('/api/issues/1/unassign', expect.objectContaining({ method: 'POST' }));
	});
});
