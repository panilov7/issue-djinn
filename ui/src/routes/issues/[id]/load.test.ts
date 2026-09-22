import { describe, expect, it, vi, afterEach } from 'vitest';
import { load } from './+page.ts';

// The detail load's serialization seam: the child direction rides from the
// page's memento into `GET /api/issues/{id}?child_direction=...`, so a flipped
// toggle survives the invalidate re-run without client-side re-sorting.

function jsonResponse(body: unknown): Response {
	return {
		ok: true,
		status: 200,
		json: () => Promise.resolve(body)
	} as Response;
}

const ISSUE = { id: 42, title: 'Watched issue', children: [] };
const COMMENTS = { comments: [] };

function stubFetch(): ReturnType<typeof vi.fn> {
	return vi.fn().mockImplementation((url: string) => {
		if (url.includes('/comments')) return Promise.resolve(jsonResponse(COMMENTS));
		return Promise.resolve(jsonResponse(ISSUE));
	});
}

/** The load's return, as the page consumes it — the test drives the load
 * directly with a partial event, so the generated `PageData` union is cast
 * aside here. */
interface LoadedDetail {
	id: number;
	issue: typeof ISSUE;
	comments: never[];
	childDirection: 'asc' | 'desc';
}

async function runLoad(fetchMock: ReturnType<typeof vi.fn>, href = '/issues/42') {
	const depends = vi.fn();
	const data = (await load({
		params: { id: '42' },
		depends,
		url: new URL(`http://localhost${href}`)
	} as never)) as LoadedDetail;
	return { data, depends, urls: fetchMock.mock.calls.map((call) => call[0]) };
}

describe('issue detail load', () => {
	afterEach(() => {
		localStorage.clear();
		vi.unstubAllGlobals();
	});

	it('loads the detail and its comments, depending on the issue-detail key', async () => {
		const fetchMock = stubFetch();
		vi.stubGlobal('fetch', fetchMock);

		const { data, depends, urls } = await runLoad(fetchMock);

		expect(data).toEqual({
			id: 42,
			issue: ISSUE,
			comments: COMMENTS.comments,
			childDirection: 'asc'
		});
		expect(depends).toHaveBeenCalledWith('app:issue-detail');
		expect(urls).toContain('/api/issues/42/comments');
	});

	it('requests children oldest-first when no memento exists', async () => {
		const fetchMock = stubFetch();
		vi.stubGlobal('fetch', fetchMock);

		const { urls } = await runLoad(fetchMock);

		expect(urls).toContain('/api/issues/42?child_direction=asc');
	});

	it('requests children newest-first when the memento holds desc', async () => {
		localStorage.setItem('issue-djinn.child-direction', JSON.stringify('desc'));
		const fetchMock = stubFetch();
		vi.stubGlobal('fetch', fetchMock);

		const { urls, data } = await runLoad(fetchMock);

		expect(urls).toContain('/api/issues/42?child_direction=desc');
		expect(data.childDirection).toBe('desc');
	});

	it('lets an explicit link param override the memento and adopt it as the choice', async () => {
		// The "...and N more" link lands here with the listing's child order —
		// the detail starts where the embed preview
		// left off, and the memento takes the linked direction on.
		localStorage.setItem('issue-djinn.child-direction', JSON.stringify('asc'));
		const fetchMock = stubFetch();
		vi.stubGlobal('fetch', fetchMock);

		const { urls, data } = await runLoad(fetchMock, '/issues/42?child_direction=desc');

		expect(urls).toContain('/api/issues/42?child_direction=desc');
		expect(data.childDirection).toBe('desc');
		expect(JSON.parse(localStorage.getItem('issue-djinn.child-direction') as string)).toBe('desc');
	});

	it('ignores an off-whitelist link param and keeps the memento', async () => {
		localStorage.setItem('issue-djinn.child-direction', JSON.stringify('asc'));
		const fetchMock = stubFetch();
		vi.stubGlobal('fetch', fetchMock);

		const { urls, data } = await runLoad(fetchMock, '/issues/42?child_direction=oldest');

		expect(urls).toContain('/api/issues/42?child_direction=asc');
		expect(data.childDirection).toBe('asc');
		expect(JSON.parse(localStorage.getItem('issue-djinn.child-direction') as string)).toBe('asc');
	});

	it('falls back to oldest-first on a wrong-shaped memento', async () => {
		localStorage.setItem('issue-djinn.child-direction', JSON.stringify('newest'));
		const fetchMock = stubFetch();
		vi.stubGlobal('fetch', fetchMock);

		const { urls, data } = await runLoad(fetchMock);

		expect(urls).toContain('/api/issues/42?child_direction=asc');
		expect(data.childDirection).toBe('asc');
	});
});