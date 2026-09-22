import { request } from './client';
import type { LabelsResponse } from '$lib/types';

// Labels are cached for the session: they are read once at startup (via the
// list route's load function) and reused by the filter UI. Mutations to labels
// go through `PATCH /api/issues/{id}` label replacement, so this cache stays
// valid for the lifetime of the page session.
let cachedLabels: string[] | null = null;

export function getAllLabels(): Promise<LabelsResponse> {
	if (cachedLabels) return Promise.resolve({ labels: cachedLabels });
	return request<LabelsResponse>('/api/labels').then((res) => {
		cachedLabels = res.labels;
		return res;
	});
}

/** Reset the session cache. Exposed for tests. */
export function clearLabelsCache(): void {
	cachedLabels = null;
}
