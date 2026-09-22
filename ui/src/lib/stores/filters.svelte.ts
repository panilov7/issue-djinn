import { createPersistedStore } from '$lib/stores/persisted.svelte';
import {
	defaultFilterSnapshot,
	parseFilterSnapshot,
	type FilterSnapshot
} from '$lib/utils/issueFilters';

const KEY = 'issue-djinn.filters';

/**
 * The issue list's persisted filter store, one instance per list component:
 * hydrated from storage at component init (so the first fetch already uses
 * the restored filters) and written back on every filter mutation. Pagination
 * is not part of the snapshot and never persists.
 */
export function createFiltersStore() {
	return createPersistedStore<FilterSnapshot>(KEY, defaultFilterSnapshot(), parseFilterSnapshot);
}
