import { getAllLabels } from '$lib/api/labels';

// The list route's server data: the static, session-cached label vocabulary
// used by the label filter. The dynamic issue list is filter-driven, so it is
// fetched by the `IssueList` component itself (the restored or default
// frontier view).
export const load = async () => {
	const { labels } = await getAllLabels();
	return { labels };
};
