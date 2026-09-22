import type { PageLoad } from './$types';
import { getIssue } from '$lib/api/issues';
import { listComments } from '$lib/api/comments';
import { resolveChildDirection } from '$lib/stores/childDirection.svelte';

// Server data (issue detail + comment thread) is owned by the load function.
// Mutations on the page call `invalidate('app:issue-detail')` to re-run this
// load — including the child-direction toggle's, so each re-run resolves the
// direction fresh (memento, or an explicit link param over it) and the server
// re-orders the children; the UI never re-sorts them client-side. The
// resolved direction rides back out so the toggle shows what was served.
export const load: PageLoad = async ({ params, depends, url }) => {
	depends('app:issue-detail');
	const id = Number(params.id);
	const childDirection = resolveChildDirection(url.searchParams.get('child_direction'));
	const [issue, comments] = await Promise.all([getIssue(id, childDirection), listComments(id)]);
	return { id, issue, comments: comments.comments, childDirection };
};
